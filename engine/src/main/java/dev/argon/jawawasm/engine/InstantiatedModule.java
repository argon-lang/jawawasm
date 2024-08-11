package dev.argon.jawawasm.engine;

import dev.argon.jawawasm.format.instructions.Instr;
import dev.argon.jawawasm.format.modules.*;
import dev.argon.jawawasm.format.modules.Module;
import dev.argon.jawawasm.format.types.*;
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

		new FunctionBuilder().build(functions);
		new TableBuilder().build(tables);
		new MemoryBuilder().build(memories);
		new GlobalBuilder().build(globals);

		elements = new WasmElements[module.elems().size()];
		for(int i = 0; i < elements.length; ++i) {
			Elem elem = module.elems().get(i);

			Object[] values = new Object[elem.init().size()];
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
						int offset = (int)evaluateInitializer(offsetExpr.body(), elem.type());
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
						int offset = (int)evaluateInitializer(offsetExpr.body(), NumType.I32);
						memory.init(offset, 0, data.init().length, data);
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
			FunctionResult.resolveWith(() -> startFunc.invoke(new Object[] {}));
		}
	}

	private final Engine engine;
	private final Module module;
	private final ModuleResolver resolver;

	private final List<WasmFunction> functions = new ArrayList<>();
	private final List<WasmTable> tables = new ArrayList<>();
	private final List<WasmMemory> memories = new ArrayList<>();
	private final List<WasmGlobal> globals = new ArrayList<>();
	private final WasmElements[] elements;
	private final Set<Integer> droppedData = new HashSet<>();


	private final Map<String, WasmModule> referencedModules = new HashMap<>();
	private final Map<String, WasmExport> exports = new HashMap<>();

	private synchronized WasmModule getReference(String name) throws ModuleResolutionException {
		WasmModule ref = referencedModules.get(name);
		if(ref == null) {
			ref = resolver.resolve(name);
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
			case ExportDesc.Mem mem -> getMemory(mem.mem());
			case ExportDesc.Global global -> getGlobal(global.global());
		};
	}

	private Object evaluateInitializer(List<? extends Instr> init, ValType type) throws ExecutionException {
		Object[] values = FunctionResult.resolveWith(() ->
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


	private void checkLimits(Limits importSpec, Limits exportedValue) throws ModuleLinkException {
		if(exportedValue.min() < importSpec.min()) {
			throw new ModuleLinkException("incompatible import type");
		}

		if(importSpec.max() == null) {
			return;
		}

		if(exportedValue.max() == null || importSpec.max() < exportedValue.max()) {
			throw new ModuleLinkException("incompatible import type");
		}
	}

	private abstract class IndexSpaceBuilder<T, TImportDesc extends ImportDesc, Def> {
		protected abstract TImportDesc castImportDesc(ImportDesc desc);
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
					throw new ModuleLinkException("unknown import");
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

	private final class FunctionBuilder extends IndexSpaceBuilder<WasmFunction, ImportDesc.Func, Func> {
		@Override
		protected ImportDesc.Func castImportDesc(ImportDesc desc) {
			return (desc instanceof ImportDesc.Func f) ? f : null;
		}

		@Override
		protected WasmFunction checkImport(ImportDesc.Func desc, WasmExport export) throws ModuleLinkException {
			if(!(export instanceof WasmFunction func)) {
				throw new ModuleLinkException("incompatible import type");
			}

			var t = getType(desc.type());

			if(!t.equals(func.type())) {
				throw new ModuleLinkException("incompatible import type");
			}

			return func;
		}

		@Override
		protected List<? extends Func> definitions() {
			return module.funcs();
		}

		@Override
		protected WasmFunction create(Func func) {
			return new WasmFunction() {
				@Override
				public FuncType type() {
					return module.types().get(func.type().index());
				}

				@Override
				public FunctionResult invoke(Object[] args) throws Throwable {
					return new StackFrame(InstantiatedModule.this, func, args).evaluate();
				}
			};
		}
	}

	private final class TableBuilder extends IndexSpaceBuilder<WasmTable, ImportDesc.Table, Table> {
		@Override
		protected ImportDesc.Table castImportDesc(ImportDesc desc) {
			return (desc instanceof ImportDesc.Table t) ? t : null;
		}

		@Override
		protected WasmTable checkImport(ImportDesc.Table desc, WasmExport export) throws ModuleLinkException {
			if(!(export instanceof WasmTable table)) {
				throw new ModuleLinkException("incompatible import type");
			}

			checkLimits(desc.type().limits(), table.type().limits());

			var t = desc.type().elementType();
			if(!t.equals(table.type().elementType())) {
				throw new ModuleLinkException("incompatible import type");
			}

			return table;
		}

		@Override
		protected List<? extends Table> definitions() {
			return module.tables();
		}

		@Override
		protected WasmTable create(Table table) {
			return new WasmTable(table.type());
		}
	}

	private final class MemoryBuilder extends IndexSpaceBuilder<WasmMemory, ImportDesc.Mem, Mem> {
		@Override
		protected ImportDesc.Mem castImportDesc(ImportDesc desc) {
			return (desc instanceof ImportDesc.Mem m) ? m : null;
		}

		@Override
		protected WasmMemory checkImport(ImportDesc.Mem desc, WasmExport export) throws ModuleLinkException {
			if(!(export instanceof WasmMemory mem)) {
				throw new ModuleLinkException("incompatible import type");
			}

			checkLimits(desc.type().limits(), mem.type().limits());

			return mem;
		}

		@Override
		protected List<? extends Mem> definitions() {
			return module.mems();
		}

		@Override
		protected WasmMemory create(Mem memory) {
			return WasmMemory.create(engine, memory.type());
		}
	}

	private final class GlobalBuilder extends IndexSpaceBuilder<WasmGlobal, ImportDesc.Global, Global> {
		@Override
		protected ImportDesc.Global castImportDesc(ImportDesc desc) {
			return (desc instanceof ImportDesc.Global g) ? g : null;
		}

		@Override
		protected WasmGlobal checkImport(ImportDesc.Global desc, WasmExport export) throws ModuleLinkException {
			if(!(export instanceof WasmGlobal global)) {
				throw new ModuleLinkException("incompatible import type");
			}

			var t = desc.type();
			if(!t.equals(global.type())) {
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
			return new WasmGlobal(global.type(), value);
		}
	}



	FuncType getType(TypeIdx index) {
		return module.types().get(index.index());
	}

	WasmFunction getFunction(FuncIdx index) {
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
