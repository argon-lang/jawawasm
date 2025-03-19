package dev.argon.jawawasm.engine.interpreter;

import dev.argon.jawawasm.engine.ModuleResolver;
import dev.argon.jawawasm.engine.internal.SubtypingBase;
import dev.argon.jawawasm.engine.internal.TypeClosure;
import dev.argon.jawawasm.engine.internal.TypeRoll;
import dev.argon.jawawasm.format.instructions.Instr;
import dev.argon.jawawasm.format.modules.*;
import dev.argon.jawawasm.format.modules.Module;
import dev.argon.jawawasm.format.types.*;
import dev.argon.jawawasm.runtime.ModuleLinkException;
import dev.argon.jawawasm.runtime.ModuleResolutionException;
import dev.argon.jawawasm.runtime.WasmMemory;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * A fully linked and instantiated module.
 */
public final class InstantiatedModule implements WasmModule {

	InstantiatedModule(Engine engine, Module module, ModuleResolver resolver) throws ExecutionException, ModuleLinkException {
		this.engine = engine;
		this.module = module;
		this.resolver = resolver;

		flatTypes = new ArrayList<>();
		for(var recType : module.types()) {
			var rolledRecType = TypeRoll.roll(recType, flatTypes.size());
			for(int i = 0; i < rolledRecType.subtypes().size(); ++i) {
				flatTypes.add(new DefType(rolledRecType, i));
			}
		}

		new FunctionBuilder().build(functions);
		new GlobalBuilder().build(globals);
		new TableBuilder().build(tables);
		new MemoryBuilder().build(memories);
		new TagBuilder().build(tags);


		elements = new WasmElements[module.elems().size()];
		for(int i = 0; i < elements.length; ++i) {
			Elem elem = module.elems().get(i);

			@Nullable Object[] values = new Object[elem.init().size()];
			for(int j = 0; j < values.length; ++j) {
				values[j] = evaluateInitializer(elem.init().get(j).body(), elem.type());
			}

			elements[i] = new WasmElements(values);
		}

		try {
			for(int i = 0; i < module.elems().size(); ++i) {
				Elem elem = module.elems().get(i);
				switch(elem.mode()) {
					case ElemMode.Active(var tableIdx, var offsetExpr) -> {
						var instElem = getElement(new ElemIdx(i));

						var table = getTable(tableIdx);
						var addrType = table.type().addrType();
						long offset = AddressTypeUtils.unboxAddress(addrType, evaluateInitializer(offsetExpr.body(), AddressTypeUtils.asNumType(addrType)));
						WasmTable.init(offset, 0, instElem.size(), table, instElem);
						dropElement(new ElemIdx(i));
					}

					case ElemMode.Declarative() -> {
						dropElement(new ElemIdx(i));
					}
					case ElemMode.Passive() -> {}
				}
			}

			for(int i = 0; i < module.datas().size(); ++i) {
				Data data = module.datas().get(i);
				switch(data.mode()) {
					case DataMode.Active(var memoryIdx, var offsetExpr) -> {
						var memory = getMemory(memoryIdx);
						var addrType = memory.addressType();
						long offset = AddressTypeUtils.unboxAddress(addrType, evaluateInitializer(offsetExpr.body(), AddressTypeUtils.asNumType(addrType)));
						memory.copyFromArray(offset, 0, data.init().length, data.init());
					}
					case DataMode.Passive() -> {}
				}
			}
		}
		catch(Throwable ex) {
			throw new ExecutionException(ex);
		}

		if(module.start() != null) {
			var startFunc = getFunction(module.start().func());
			DynamicFunctionResult.resolveWith(() -> startFunc.invoke(new Object[] {}));
		}
	}

	private final Engine engine;
	private final Module module;
	private final ModuleResolver<WasmModule> resolver;

	private final List<DefType> flatTypes;
	private final List<DynamicWasmFunction> functions = new ArrayList<>();
	private final List<WasmTable> tables = new ArrayList<>();
	private final List<WasmMemory> memories = new ArrayList<>();
	private final List<WasmGlobal> globals = new ArrayList<>();
	private final List<DynamicWasmTag> tags = new ArrayList<>();
	private final WasmElements[] elements;
	private final Set<Integer> droppedData = new HashSet<>();


	private final Map<String, WasmModule> referencedModules = new HashMap<>();
	private final Map<String, WasmExport> exports = new HashMap<>();

	final TypeClosure closure = new TypeClosure() {
		@Override
		public HeapType resolveTypeIdx(TypeIdx idx) {
			return resolveHeapType(flatTypes.get(idx.index()));
		}
	};

	final SubtypingBase subtyping = new SubtypingBase() {
		@Override
		public HeapType resolveTypeIdx(TypeIdx idx) {
			throw new RuntimeException("Unexpected type index");
		}
	};


	private synchronized WasmModule getReference(String name) throws ModuleResolutionException {
		WasmModule ref = referencedModules.get(name);
		if(ref == null) {
			ref = resolver.resolve(this, name);
			referencedModules.put(name, ref);
		}
		return ref;
	}

	@Override
	public synchronized @Nullable WasmExport getExport(String name) {
		WasmExport export = exports.get(name);
		if(export == null) {
			ExportDesc desc = module.exports()
					.stream()
					.filter((Export e) -> e.name().equals(name))
					.map(Export::desc)
					.findFirst()
					.orElse(null);

			if(desc == null) {
				return null;
			}


			export = createExport(desc);
			exports.put(name, export);
		}

		return export;
	}

	private WasmExport createExport(ExportDesc export) {
		return switch(export) {
			case ExportDesc.Func func -> getFunction(func.func());
			case ExportDesc.Table table -> getTable(table.table());
			case ExportDesc.Mem mem -> new WasmMemoryExport(getMemory(mem.mem()));
			case ExportDesc.Global global -> getGlobal(global.global());
			case ExportDesc.Tag tag -> getTag(tag.tag());
		};
	}

	private @Nullable Object evaluateInitializer(List<? extends Instr> init, ValType type) throws ExecutionException {
		@Nullable Object[] values = DynamicFunctionResult.resolveWith(() ->
				new StackFrame(
						InstantiatedModule.this,
						init,
						new FuncType(
								new ResultType(List.of()),
								new ResultType(List.of(type))
						),
						new Object[] {},
						new Object[] {}
				)
						.evaluate()
		);
		return values[0];
	}

	private abstract class IndexSpaceBuilder<T, TImportDesc extends ImportDesc, Def> {
		protected abstract @Nullable TImportDesc castImportDesc(ImportDesc desc);
		protected abstract T checkImport(TImportDesc desc, WasmExport export) throws ModuleLinkException;

		protected abstract List<? extends Def> definitions();
		protected abstract T create(Def def) throws ExecutionException;

		public final void build(List<T> items) throws ModuleLinkException {
			for(Import imp : module.imports()) {
				TImportDesc desc = castImportDesc(imp.desc());
				if(desc == null) {
					continue;
				}

				var mod = getReference(imp.module());

				var export = mod.getExport(imp.name());
				if(export == null) {
					throw new ModuleLinkException("unknown import " + imp.name());
				}

				items.add(checkImport(desc, export));
			}

			for(var def : definitions()) {
				T item;
				try {
					item = create(def);
				}
				catch(ExecutionException ex) {
					throw new ModuleLinkException(ex);
				}

				items.add(item);
			}
		}

	}

	private final class FunctionBuilder extends IndexSpaceBuilder<DynamicWasmFunction, ImportDesc.Func, Func> {
		@Override
		protected ImportDesc.@Nullable Func castImportDesc(ImportDesc desc) {
			return (desc instanceof ImportDesc.Func f) ? f : null;
		}

		@Override
		protected DynamicWasmFunction checkImport(ImportDesc.Func desc, WasmExport export) throws ModuleLinkException {
			if(!(export instanceof DynamicWasmFunction func)) {
				throw new ModuleLinkException("incompatible import type");
			}

			var importDefType = func.type();
			var defType = getDefType(desc.type());

			if(!subtyping.isSubtypeDefType(importDefType, defType)) {
				throw new ModuleLinkException("incompatible import type: Expected " + defType + ", Actual " + importDefType);
			}

			return func;
		}

		@Override
		protected List<? extends Func> definitions() {
			return module.funcs();
		}

		@Override
		protected DynamicWasmFunction create(Func func) {
			return new DynamicWasmFunction() {
				@Override
				public DefType type() {
					return closure.resolveDefType(getDefType(func.type()));
				}

				@Override
				public FuncType functionType() {
					return getFuncType(func.type());
				}

				@Override
				public DynamicFunctionResult invoke(@Nullable Object[] args) throws Throwable {
					return new StackFrame(InstantiatedModule.this, func, args).evaluate();
				}
			};
		}
	}

	private final class TableBuilder extends IndexSpaceBuilder<WasmTable, ImportDesc.Table, Table> {
		@Override
		protected ImportDesc.@Nullable Table castImportDesc(ImportDesc desc) {
			return (desc instanceof ImportDesc.Table t) ? t : null;
		}

		@Override
		protected WasmTable checkImport(ImportDesc.Table desc, WasmExport export) throws ModuleLinkException {
			if(!(export instanceof WasmTable table)) {
				throw new ModuleLinkException("incompatible import type");
			}

			if(!subtyping.isSubtypeTable(table.type(), closure.resolveTableType(desc.type()))) {
				throw new ModuleLinkException("incompatible import type");
			}

			return table;
		}

		@Override
		protected List<? extends Table> definitions() {
			return module.tables();
		}

		@Override
		protected WasmTable create(Table table) throws ExecutionException {
			var tableType = closure.resolveTableType(table.type());
			var initialValue = evaluateInitializer(table.init().body(), tableType.elementType());

			return new WasmTable(tableType, initialValue);
		}
	}

	private final class MemoryBuilder extends IndexSpaceBuilder<WasmMemory, ImportDesc.Mem, Mem> {
		@Override
		protected ImportDesc.@Nullable Mem castImportDesc(ImportDesc desc) {
			return (desc instanceof ImportDesc.Mem m) ? m : null;
		}

		@Override
		protected WasmMemory checkImport(ImportDesc.Mem desc, WasmExport export) throws ModuleLinkException {
			if(!(export instanceof WasmMemoryExport(var mem))) {
				throw new ModuleLinkException("incompatible import type");
			}

			var memType = AddressTypeUtils.getMemoryType(mem);

			if(!subtyping.isSubtypeMemory(memType, desc.type())) {
				throw new ModuleLinkException("incompatible import type");
			}

			return mem;
		}

		@Override
		protected List<? extends Mem> definitions() {
			return module.mems();
		}

		@Override
		protected WasmMemory create(Mem memory) {
			return WasmMemory.create(engine.getAllocator(), memory.type().addrType(), memory.type().limits().min(), memory.type().limits().max());
		}
	}

	private final class GlobalBuilder extends IndexSpaceBuilder<WasmGlobal, ImportDesc.Global, Global> {
		@Override
		protected ImportDesc.@Nullable Global castImportDesc(ImportDesc desc) {
			return (desc instanceof ImportDesc.Global g) ? g : null;
		}

		@Override
		protected WasmGlobal checkImport(ImportDesc.Global desc, WasmExport export) throws ModuleLinkException {
			if(!(export instanceof WasmGlobal global)) {
				throw new ModuleLinkException("incompatible import type");
			}

			if(!subtyping.isSubtypeGlobal(global.type(), closure.resolveGlobalType(desc.type()))) {
				throw new ModuleLinkException("incompatible import type");
			}

			return global;
		}

		@Override
		protected List<? extends Global> definitions() {
			return module.globals();
		}

		@Override
		protected WasmGlobal create(Global global) throws ExecutionException {
			Object value = evaluateInitializer(global.init().body(), global.type().type());
			return new WasmGlobal(closure.resolveGlobalType(global.type()), value);
		}
	}

	private final class TagBuilder extends IndexSpaceBuilder<DynamicWasmTag, ImportDesc.Tag, Tag> {
		@Override
		protected ImportDesc.@Nullable Tag castImportDesc(ImportDesc desc) {
			return (desc instanceof ImportDesc.Tag t) ? t : null;
		}

		@Override
		protected DynamicWasmTag checkImport(ImportDesc.Tag desc, WasmExport export) throws ModuleLinkException {
			if(!(export instanceof DynamicWasmTag tag)) {
				throw new ModuleLinkException("incompatible import type");
			}

			var importDefType = tag.type();
			var defType = getDefType(desc.type().funcType());

			if(!subtyping.isSubtypeDefType(importDefType, defType)) {
				throw new ModuleLinkException("incompatible import type: Expected " + defType + ", Actual " + importDefType);
			}

			return tag;
		}

		@Override
		protected List<? extends Tag> definitions() {
			return module.tags();
		}

		@Override
		protected DynamicWasmTag create(Tag tag) throws ExecutionException {
			var defType = getDefType(tag.type().funcType());
			var funcType = getFuncType(tag.type().funcType());
			return new DynamicWasmTag(defType, funcType);
		}
	}

	DefType getDefType(TypeIdx index) {
		return closure.resolveDefType(flatTypes.get(index.index()));
	}

	FuncType getFuncType(TypeIdx index) {
		var defType = getDefType(index);
		var subType = defType.recursiveType().subtypes().get(defType.index());
		var funcType = (FuncType)subType.compositeType();
		return closure.resolveFuncType(funcType);
	}

	StructType getStructType(TypeIdx index) {
		var defType = getDefType(index);
		var subType = defType.recursiveType().subtypes().get(defType.index());
		var compositeType = closure.resolveCompositeType(subType.compositeType());
		return (StructType)compositeType;
	}

	ArrayType getArrayType(TypeIdx index) {
		var defType = getDefType(index);
		var subType = defType.recursiveType().subtypes().get(defType.index());
		var compositeType = closure.resolveCompositeType(subType.compositeType());
		return (ArrayType)compositeType;
	}

	DynamicWasmFunction getFunction(FuncIdx index) {
		return functions.get(index.index());
	}

	WasmTable getTable(TableIdx index) {
		return tables.get(index.index());
	}

	WasmMemory getMemory(MemIdx index) {
		return memories.get(index.index());
	}

	WasmGlobal getGlobal(GlobalIdx index) {
		return globals.get(index.index());
	}

	DynamicWasmTag getTag(TagIdx index) {
		return tags.get(index.index());
	}

	WasmElements getElement(ElemIdx index) {
		synchronized(elements) {
			return elements[index.index()];
		}
	}

	Data getData(DataIdx index) {
		synchronized(droppedData) {
			if(droppedData.contains(index.index())) {
				return new Data(new byte[] {}, new DataMode.Passive());
			}

			return module.datas().get(index.index());
		}
	}


	void dropElement(ElemIdx index) {
		synchronized(elements) {
			elements[index.index()] = new WasmElements(new Object[] {});
		}
	}

	void dropData(DataIdx index) {
		synchronized(droppedData) {
			droppedData.add(index.index());
		}
	}


}
