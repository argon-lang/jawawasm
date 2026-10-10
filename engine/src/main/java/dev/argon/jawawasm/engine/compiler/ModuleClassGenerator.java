package dev.argon.jawawasm.engine.compiler;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import dev.argon.jawawasm.engine.ModuleResolver;
import dev.argon.jawawasm.engine.internal.*;
import dev.argon.jawawasm.format.instructions.*;
import dev.argon.jawawasm.format.modules.*;
import dev.argon.jawawasm.format.modules.Module;
import dev.argon.jawawasm.format.types.*;
import dev.argon.jawawasm.runtime.AddrType;
import dev.argon.jawawasm.runtime.ModuleLinkException;
import dev.argon.jawawasm.runtime.ModuleResolutionException;
import dev.argon.jawawasm.runtime.V128;
import org.jspecify.annotations.Nullable;

import java.lang.classfile.*;
import java.lang.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.constant.*;
import java.util.*;
import java.util.function.Consumer;

import static dev.argon.jawawasm.engine.internal.Constants.*;
import static dev.argon.jawawasm.engine.compiler.NameMangling.escapeName;
import static dev.argon.jawawasm.engine.compiler.WasmClassGeneratorUtils.*;
import static java.lang.constant.ConstantDescs.*;

class ModuleClassGenerator extends WasmClassGenerator {
	ModuleClassGenerator(
		ModuleCompiler compiler, Module module, String className, ModuleResolver<WasmModuleRealization> resolver
	) {
		super(compiler);
		this.module = module;
		this.simpleClassName = className;
		this.className = ClassDesc.of(compiler.getOptions().javaPackage(), className);
		this.resolver = resolver;
	}

	private final Module module;
	private final String simpleClassName;
	private final ClassDesc className;
	private final ModuleResolver<WasmModuleRealization> resolver;

	private String functionName(int index) {
		var prefix = "func" + index;
		var names = module.name();
		if(names == null) {
			return prefix;
		}
		var name = names.functionNames().get(index);
		return name == null ? prefix : prefix + "_" + NameMangling.sanitizeName(name);
	}

	private void emitLocalName(CodeBuilder cb, int functionIndex, int localIndex, LocalInfo local, Label start) {
		var names = module.name();
		if(names == null) {
			return;
		}
		var localNames = names.localNames().get(functionIndex);
		if(localNames == null) {
			return;
		}
		var name = localNames.get(localIndex);
		if(name != null) {
			cb.localVariable(local.slotIndex(), name, local.type(), start, cb.endLabel());
		}
	}

	private final List<DefType> types = new ArrayList<>();
	private final List<String> importModules = new ArrayList<>();
	private final Map<String, ImportModuleInfo> importModuleInfos = new HashMap<>();
	private final List<FunctionInfo> funcs = new ArrayList<>();
	private final List<TableInfo> tables = new ArrayList<>();
	private final List<GlobalInfo> globals = new ArrayList<>();
	private final List<MemInfo> mems = new ArrayList<>();
	private final List<TagInfo> tags = new ArrayList<>();
	private final List<ElemInfo> elems = new ArrayList<>();
	private final List<WasmExportRealization> exports = new ArrayList<>();

	private static final int MAX_FUNCTIONS_PER_CLASS = 100;
	private static final int MAX_INIT_ELEMS_PER_CLASS = 500;

	private byte @Nullable [] cachedBytecode = null;

	@Override
	public ClassDesc className() {
		return className;
	}

	WasmModuleRealization realization() {
		return new WasmModuleRealization(className, ImmutableList.copyOf(exports));
	}

	@Override
	public ClassHierarchyResolver.ClassHierarchyInfo hierarchyInfo() {
		return ClassHierarchyResolver.ClassHierarchyInfo.ofClass(wasmModuleClass);
	}

	@Override
	protected byte[] generateImpl() {
		if(cachedBytecode == null) {
			cachedBytecode = compiler.classFile().build(className(), this::buildClass);
		}
		return cachedBytecode;
	}

	private final TypeClosure closure = new TypeClosure() {
		@Override
		public HeapType resolveTypeIdx(TypeIdx idx) {
			return resolveHeapType(types.get(idx.index()));
		}
	};

	final SubtypingBase subtyping = new SubtypingBase() {
		@Override
		public HeapType resolveTypeIdx(TypeIdx idx) {
			throw new RuntimeException("Unexpected type index");
		}
	};

	private void buildClass(ClassBuilder clb) {
		types.clear();
		importModules.clear();
		importModuleInfos.clear();
		funcs.clear();
		tables.clear();
		globals.clear();
		mems.clear();
		elems.clear();
		exports.clear();

		clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL);
		clb.withSuperclass(wasmModuleClass);

		for(var recType : module.types()) {
			var rolledRecType = closure.resolveRecursiveType(TypeRoll.roll(recType, types.size()));
			for(int i = 0; i < recType.subtypes().size(); ++i) {
				types.add(new DefType(rolledRecType, i));
			}
		}

		var names = module.name();
		if(names != null) {
			for(int i = 0; i < types.size(); ++i) {
				compiler.registerTypeNames(
					types.get(i),
					names.typeNames().get(i),
					names.fieldNames().getOrDefault(i, com.google.common.collect.ImmutableMap.of())
				);
			}
		}

		List<Consumer<CodeBuilder>> constructorInits = new ArrayList<>();
		List<ClassDesc> constructorParams = new ArrayList<>();

		List<FuncsClassGenerator> funcsClassGenerators = new ArrayList<>();
		FuncsClassGenerator funcsClassGenerator = new FuncsClassGenerator(0);
		funcsClassGenerators.add(funcsClassGenerator);

		List<ElemsInitClassGenerator> elemsInitClassGenerators = new ArrayList<>();

		constructorParams.add(ClassDesc.of(RUNTIME_PACKAGE, "RuntimeContext"));

		int constructorParamSlot = 2;

		for(var imp : module.imports()) {
			int importIndex = importModules.indexOf(imp.module());
			ImportModuleInfo importModuleInfo;
			if(importIndex < 0) {
				importIndex = importModules.size();
				importModules.add(imp.module());

				WasmModuleRealization importRealization;
				try {
					importRealization = resolver.resolve(imp.module());
				}
				catch(ModuleResolutionException e) {
					throw new ModuleLinkException(e);
				}
				importModuleInfo = new ImportModuleInfo(imp.module(), "import" + importIndex, importRealization);
				importModuleInfos.put(imp.module(), importModuleInfo);

				clb.withField(importModuleInfo.fieldName, importRealization.classDesc(), ClassFile.ACC_FINAL);

				int slot = constructorParamSlot;
				++constructorParamSlot;
				constructorParams.add(importRealization.classDesc());
				constructorInits.add(cb -> {
					cb.aload(0);
					cb.aload(slot);
					cb.putfield(className, importModuleInfo.fieldName, importRealization.classDesc());
				});
			}
			else {
				importModuleInfo = importModuleInfos.get(imp.module());
				Objects.requireNonNull(importModuleInfo);
			}

			String localName;

			var exportRealization = importModuleInfo.realization.exports().stream()
				.filter(e -> e.exportName().equals(imp.name())).findFirst().orElse(null);

			if(exportRealization == null) {
				throw new ModuleLinkException("unknown import " + imp.name());
			}

			switch(imp.desc()) {
				case ImportDesc.Func func -> {
					if(!(exportRealization instanceof WasmExportRealization.OfInstanceMethod exportRealMethod)
						|| !(exportRealMethod.externalType() instanceof DefType importDefType)) {
						throw new ModuleLinkException("incompatible import type");
					}

					localName = functionName(funcs.size());
					var defType = closure.resolveDefType(types.get(func.type().index()));

					if(!subtyping.isSubtypeDefType(importDefType, defType)) {
						throw new ModuleLinkException(
							"incompatible import type: import " + imp.name() + " from " + imp.module() + " Expected "
								+ defType + ", Actual " + importDefType
						);
					}

					var type = compiler.getMethodType(defType);
					funcs.add(
						new FunctionInfo(
							localName,
							funcsClassGenerator.className(),
							invokeMethodType(type.descriptor()),
							lambdaMethodType(type.descriptor()),
							type,
							getFuncType(defType),
							defType
						)
					);

					funcsClassGenerator.addFunctionImport(
						funcs.size() - 1,
						importModuleInfo,
						localName,
						type.descriptor(),
						exportRealMethod
					);

					if(funcsClassGenerator.functionCount() >= MAX_FUNCTIONS_PER_CLASS) {
						funcsClassGenerator = new FuncsClassGenerator(funcsClassGenerators.size());
						funcsClassGenerators.add(funcsClassGenerator);
					}
				}
				case ImportDesc.Table table -> {
					if(!(exportRealization instanceof WasmExportRealization.OfInstanceMethod exportRealMethod)
						|| !(exportRealMethod.externalType() instanceof TableType importTableType)) {
						throw new ModuleLinkException("incompatible import type");
					}

					localName = "table" + tables.size();
					var tableType = closure.resolveTableType(table.type());
					var elementType = compiler.getValType(tableType.elementType()).type();

					if(!subtyping.isSubtypeTableIgnoreMin(importTableType, closure.resolveTableType(table.type()))
						|| tableType.limits().min() < 0 || tableType.limits().min() > Integer.MAX_VALUE) {
						throw new ModuleLinkException("incompatible import type");
					}

					tables.add(new TableInfo(localName, elementType, tableType));
					clb.withField(localName, wasmTable, ClassFile.ACC_FINAL);

					constructorInits.add(cb -> {
						cb.aload(0);
						cb.aload(0);
						cb.getfield(className, importModuleInfo.fieldName, importModuleInfo.realization().classDesc());
						cb.invokevirtual(
							importModuleInfo.realization().classDesc(),
							exportRealMethod.methodName(),
							exportRealMethod.methodType()
						);
						cb.putfield(className, localName, wasmTable);

						cb.aload(0);
						cb.getfield(className, localName, wasmTable);
						cb.loadConstant((int)tableType.limits().min());
						cb.invokevirtual(wasmTable, "ensureMinimumSize", MethodTypeDesc.of(CD_void, CD_int));
					});
				}
				case ImportDesc.Global global -> {
					if(!(exportRealization instanceof WasmExportRealization.OfInstanceMethod exportRealMethod)
						|| !(exportRealMethod.externalType() instanceof GlobalType importGlobalType)) {
						throw new ModuleLinkException("incompatible import type");
					}

					localName = "global" + globals.size();

					var globalType = closure.resolveGlobalType(global.type());

					if(!subtyping.isSubtypeGlobal(importGlobalType, globalType)) {
						throw new ModuleLinkException("incompatible import type");
					}

					ClassDesc elementType = compiler.getValType(globalType.type()).type();
					ClassDesc containerType = getGlobalContainerType(globalType, elementType);
					globals.add(new GlobalInfo(localName, containerType, elementType, globalType));

					clb.withField(localName, containerType, ClassFile.ACC_FINAL);

					constructorInits.add(cb -> {
						cb.aload(0);
						cb.aload(0);
						cb.getfield(className, importModuleInfo.fieldName, importModuleInfo.realization().classDesc());
						cb.invokevirtual(
							importModuleInfo.realization().classDesc(),
							exportRealMethod.methodName(),
							exportRealMethod.methodType()
						);
						cb.putfield(className, localName, containerType);
					});
				}
				case ImportDesc.Mem mem -> {
					if(!(exportRealization instanceof WasmExportRealization.OfInstanceMethod exportRealMethod)
						|| !(exportRealMethod.externalType() instanceof MemType importMemType)) {
						throw new ModuleLinkException("incompatible import type");
					}

					if(!subtyping.isSubtypeMemoryIgnoreMin(importMemType, mem.type())
						|| mem.type().limits().min() < 0) {
						throw new ModuleLinkException("incompatible import type");
					}

					localName = "mem" + mems.size();
					mems.add(new MemInfo(localName, mem.type()));
					var wasmMemory = mems.getLast().memoryClass();

					clb.withField(localName, wasmMemory, ClassFile.ACC_FINAL);

					constructorInits.add(cb -> {
						cb.aload(0);
						cb.aload(0);
						cb.getfield(className, importModuleInfo.fieldName, importModuleInfo.realization().classDesc());
						cb.invokevirtual(
							importModuleInfo.realization().classDesc(),
							exportRealMethod.methodName(),
							exportRealMethod.methodType()
						);
						cb.checkcast(wasmMemory);
						cb.putfield(className, localName, wasmMemory);

						cb.aload(0);
						cb.getfield(className, localName, wasmMemory);
						var at = addrDesc(mem.type().addrType());
						if(mem.type().addrType() == AddrType.I32) {
							cb.loadConstant(Math.toIntExact(mem.type().limits().min()));
						}
						else {
							cb.loadConstant(mem.type().limits().min());
						}
						cb.invokevirtual(wasmMemory, "ensureMinimumSize", MethodTypeDesc.of(CD_void, at));
					});
				}
				case ImportDesc.Tag tag -> {
					if(!(exportRealization instanceof WasmExportRealization.OfInnerClass exportRealClass)) {
						throw new ModuleLinkException("incompatible import type");
					}

					var defType = closure.resolveDefType(types.get(tag.type().funcType().index()));
					var funcType = getFuncType(defType);

					if(!subtyping.isSubtypeDefType(exportRealClass.tagDefType(), defType)) {
						throw new ModuleLinkException("incompatible import type");
					}

					tags.add(
						new TagInfo(
							new TagRealization(
								exportRealClass.classDesc(),
								exportRealClass.innerClass(),
								exportRealClass.constructorType()
							),
							funcType,
							defType
						)
					);
				}
			}
		}

		int constructorTempVarSlot = importModules.size() + 2;

		for(var func : module.funcs()) {
			var name = functionName(funcs.size());
			var defType = closure.resolveDefType(types.get(func.type().index()));
			var type = compiler.getMethodType(defType);
			funcs.add(
				new FunctionInfo(
					name,
					funcsClassGenerator.className(),
					invokeMethodType(type.descriptor()),
					lambdaMethodType(type.descriptor()),
					type,
					getFuncType(defType),
					defType
				)
			);

			funcsClassGenerator.addFunction(funcs.size() - 1, name, type.descriptor(), func);
			if(funcsClassGenerator.functionCount() >= MAX_FUNCTIONS_PER_CLASS) {
				funcsClassGenerator = new FuncsClassGenerator(funcsClassGenerators.size());
				funcsClassGenerators.add(funcsClassGenerator);
			}
		}

		for(var global : module.globals()) {
			var name = "global" + globals.size();
			var globalType = closure.resolveGlobalType(global.type());
			ClassDesc elementType = compiler.getValType(globalType.type()).type();
			ClassDesc containerType = getGlobalContainerType(globalType, elementType);

			clb.withField(name, containerType, ClassFile.ACC_FINAL);

			constructorInits.add(cb -> {
				cb.aload(0);

				if(globalType.mutability() == Mut.Var) {
					cb.new_(containerType);
					cb.dup();
				}

				var bytecodeGen = new BytecodeGenerator(
					cb,
					new LocalInfo[]{},
					new ResultType(ImmutableList.of(globalType.type())),
					0,
					constructorTempVarSlot
				);
				bytecodeGen.generateInstructionBlock(global.init());

				if(globalType.mutability() == Mut.Var) {
					cb.invokespecial(
						containerType,
						"<init>",
						MethodTypeDesc.of(CD_void, elementType.isPrimitive() ? elementType : CD_Object)
					);
				}

				cb.putfield(className, name, containerType);
			});

			globals.add(new GlobalInfo(name, containerType, elementType, globalType));
		}

		for(var table : module.tables()) {
			var name = "table" + tables.size();

			var tableType = closure.resolveTableType(table.type());
			var elementType = compiler.getValType(tableType.elementType()).type();

			tables.add(new TableInfo(name, elementType, tableType));
			clb.withField(name, wasmTable, ClassFile.ACC_FINAL);

			constructorInits.add(cb -> {
				cb.aload(0);
				loadLimits(cb, tableType.limits());

				var bytecodeGen = new BytecodeGenerator(
					cb,
					new LocalInfo[]{},
					new ResultType(ImmutableList.of(table.type().elementType())),
					0,
					constructorTempVarSlot
				);
				bytecodeGen.generateInstructionBlock(table.init());

				cb.invokestatic(wasmTable, "create", MethodTypeDesc.of(wasmTable, CD_long, CD_Long, CD_Object));
				cb.putfield(className, name, wasmTable);
			});
		}

		for(var memory : module.mems()) {
			var name = "mem" + mems.size();
			var memInfo = new MemInfo(name, memory.type());
			mems.add(memInfo);
			var wasmMemory = memInfo.memoryClass();
			var at = addrDesc(memory.type().addrType());
			var boxedAt = at.equals(CD_int) ? CD_Integer : CD_Long;
			clb.withField(name, wasmMemory, ClassFile.ACC_FINAL);
			constructorInits.add(cb -> {
				var runtimeOptions = ClassDesc.of(RUNTIME_PACKAGE, "RuntimeOptions");
				cb.aload(0);
				cb.aload(1);
				cb.invokeinterface(
					ClassDesc.of(RUNTIME_PACKAGE, "RuntimeContext"),
					"options",
					MethodTypeDesc.of(runtimeOptions)
				);
				var limits = memory.type().limits();
				if(at.equals(CD_int)) {
					cb.loadConstant(Math.toIntExact(limits.min()));
				}
				else {
					cb.loadConstant(limits.min());
				}
				if(limits.max() == null) {
					cb.aconst_null();
				}
				else {
					if(at.equals(CD_int)) {
						cb.loadConstant(Math.toIntExact(limits.max()));
					}
					else {
						cb.loadConstant(limits.max());
					}
					cb.invokestatic(boxedAt, "valueOf", MethodTypeDesc.of(boxedAt, at));
				}
				cb.invokestatic(wasmMemory, "create", MethodTypeDesc.of(wasmMemory, runtimeOptions, at, boxedAt));
				cb.putfield(className, name, wasmMemory);
			});
		}

		for(var elem : module.elems()) {
			var fieldName = "elem" + elems.size();

			var elementType = compiler.getValType(closure.resolveValType(elem.type())).type();
			var arrayType = elementType.arrayType();

			elems.add(new ElemInfo(fieldName, arrayType, elementType, elem));

			clb.withField(fieldName, arrayType, 0);
			constructorInits.add(cb -> {
				var elemSize = elem.mode() instanceof ElemMode.Declarative ? 0 : elem.init().size();

				cb.aload(0);
				cb.loadConstant(elemSize);
				cb.anewarray(elementType);
				cb.putfield(className, fieldName, arrayType);

				for(int i = 0; i < elemSize; i += MAX_INIT_ELEMS_PER_CLASS) {
					cb.aload(0);
					cb.aload(0);
					cb.getfield(className, fieldName, arrayType);

					int chunkSize = Math.min(i + MAX_INIT_ELEMS_PER_CLASS, elem.init().size());

					var initGen = new ElemsInitClassGenerator(
						elemsInitClassGenerators.size(),
						elem.type(),
						elementType,
						i,
						elem.init().subList(i, chunkSize)
					);
					elemsInitClassGenerators.add(initGen);

					cb.invokestatic(
						initGen.className(),
						ElemsInitClassGenerator.INIT_METHOD_NAME,
						initGen.initMethodType()
					);
				}

				if(elem.mode() instanceof ElemMode.Active(var tableIdx, var offset)) {
					var table = tables.get(tableIdx.index());
					var at = addrDesc(table.tableType.addrType());

					var bytecodeGen = new BytecodeGenerator(
						cb,
						new LocalInfo[]{},
						new ResultType(ImmutableList.of(addrValType(table.tableType.addrType()))),
						0,
						constructorTempVarSlot
					);
					bytecodeGen.generateInstructionBlock(offset);

					cb.iconst_0();

					cb.aload(0);
					cb.getfield(className, fieldName, arrayType);
					cb.arraylength();

					cb.aload(0);
					cb.getfield(className, fieldName, arrayType);

					cb.aload(0);
					cb.getfield(className, table.fieldName, wasmTable);

					cb.invokestatic(
						wasmTable,
						"copyFromArray",
						MethodTypeDesc.of(CD_void, at, CD_int, CD_int, CD_Object.arrayType(), wasmTable)
					);

					cb.aload(0);
					cb.iconst_0();
					cb.anewarray(elementType);
					cb.putfield(className, fieldName, arrayType);
				}
			});
		}

		for(int i = 0; i < module.datas().size(); ++i) {
			int index = i;
			var data = module.datas().get(i);
			var fieldName = "data" + i;
			clb.withField(fieldName, CD_byte.arrayType(), 0);
			constructorInits.add(cb -> {
				var inputStream = ClassDesc.of("java.io.InputStream");

				cb.aload(0);
				cb.loadConstant(className);
				cb.loadConstant(simpleClassName + ".data" + index + ".dat");
				cb.invokevirtual(CD_Class, "getResourceAsStream", MethodTypeDesc.of(inputStream, CD_String));
				cb.invokestatic(
					ClassDesc.of(RUNTIME_PACKAGE, "Util"),
					"readData",
					MethodTypeDesc.of(CD_byte.arrayType(), inputStream)
				);
				cb.putfield(className, fieldName, CD_byte.arrayType());

				if(data.mode() instanceof DataMode.Active(var memIdx, var offset)) {
					var mem = mems.get(memIdx.index());
					var wasmMemory = mem.memoryClass();
					var at = addrDesc(mem.memType().addrType());

					var bytecodeGen = new BytecodeGenerator(
						cb,
						new LocalInfo[]{},
						new ResultType(ImmutableList.of(addrValType(mem.memType().addrType()))),
						0,
						constructorTempVarSlot
					);
					bytecodeGen.generateInstructionBlock(offset);

					cb.iconst_0();

					cb.aload(0);
					cb.getfield(className, fieldName, CD_byte.arrayType());
					cb.arraylength();

					cb.aload(0);
					cb.getfield(className, fieldName, CD_byte.arrayType());

					cb.aload(0);
					cb.getfield(className, mem.fieldName, wasmMemory);

					cb.invokestatic(
						wasmMemory,
						"copyFromArray",
						MethodTypeDesc.of(CD_void, at, CD_int, CD_int, CD_byte.arrayType(), wasmMemory)
					);

					cb.aload(0);
					cb.iconst_0();
					cb.newarray(TypeKind.BYTE);
					cb.putfield(className, fieldName, CD_byte.arrayType());
				}
			});
			compiler.enqueueGenerator(new WasmResourceGenerator() {
				@Override
				public String resourceName() {
					var desc = className.descriptorString();
					return desc.substring(1, desc.length() - 1) + ".data" + index + ".dat";
				}

				@Override
				public byte[] generate() {
					return data.init().toByteArray();
				}
			});
		}

		generateTags();

		generateConstructor(clb, constructorInits, constructorParams);

		for(var export : module.exports()) {
			switch(export.desc()) {
				case ExportDesc.Func func -> {
					var funcInfo = funcs.get(func.func().index());
					var methodName = escapeName(export.name());
					var methodType = funcInfo.methodType();
					clb.withMethod(methodName, methodType.descriptor(), ClassFile.ACC_PUBLIC, mb -> {
						mb.with(RuntimeVisibleTypeAnnotationsAttribute.of(methodType.methodTypeAnnotations()));
						mb.withCode(cb -> {
							int slotIndex = 1;
							for(var t : methodType.descriptor().parameterList()) {
								cb.loadLocal(typeKind(t), slotIndex);
								slotIndex += slotSize(t);
							}

							cb.aload(0);

							cb.invokestatic(funcInfo.methodsClassName(), funcInfo.name(), funcInfo.invokeMethodType());
							cb.areturn();
						});
					});
					exports.add(
						new WasmExportRealization.OfInstanceMethod(
							export.name(),
							methodName,
							methodType.descriptor(),
							funcInfo.defType
						)
					);
				}
				case ExportDesc.Global global -> {
					var globalInfo = globals.get(global.global().index());
					var methodName = escapeName(export.name());
					var globalType = globalInfo.containerType();
					var methodType = MethodTypeDesc.of(globalType);
					clb.withMethodBody(methodName, methodType, ClassFile.ACC_PUBLIC, cb -> {
						cb.aload(0);
						cb.getfield(className, globalInfo.fieldName, globalType);
						if(globalType.isPrimitive()) {
							cb.return_(typeKind(globalType));
						}
						else {
							cb.areturn();
						}
					});
					exports.add(
						new WasmExportRealization.OfInstanceMethod(
							export.name(),
							methodName,
							methodType,
							globalInfo.globalType()
						)
					);
				}
				case ExportDesc.Mem mem -> {
					var memInfo = mems.get(mem.mem().index());
					var wasmMemory = memInfo.memoryClass();
					var methodName = escapeName(export.name());
					var methodType = MethodTypeDesc.of(wasmMemory);
					clb.withMethodBody(methodName, methodType, ClassFile.ACC_PUBLIC, cb -> {
						cb.aload(0);
						cb.getfield(className, memInfo.fieldName, wasmMemory);
						cb.areturn();
					});
					exports.add(
						new WasmExportRealization.OfInstanceMethod(
							export.name(),
							methodName,
							methodType,
							memInfo.memType()
						)
					);
				}
				case ExportDesc.Table table -> {
					var tableInfo = tables.get(table.table().index());
					var methodName = escapeName(export.name());
					var methodType = MethodTypeDesc.of(wasmTable);
					clb.withMethodBody(methodName, methodType, ClassFile.ACC_PUBLIC, cb -> {
						cb.aload(0);
						cb.getfield(className, tableInfo.fieldName, wasmTable);
						cb.areturn();
					});
					exports.add(
						new WasmExportRealization.OfInstanceMethod(
							export.name(),
							methodName,
							methodType,
							tableInfo.tableType()
						)
					);
				}
				case ExportDesc.Tag tag -> {
					var tagInfo = tags.get(tag.tag().index());

					exports.add(
						new WasmExportRealization.OfInnerClass(
							export.name(),
							tagInfo.realization().classDesc(),
							tagInfo.realization().innerClassInfo(),
							tagInfo.realization().constructorType(),
							tagInfo.funcType,
							tagInfo.defType
						)
					);
				}
			}
		}

		for(var methodClassGen : funcsClassGenerators) {
			compiler.enqueueGenerator(methodClassGen);
		}

		for(var elemsInitGen : elemsInitClassGenerators) {
			compiler.enqueueGenerator(elemsInitGen);
		}
	}

	private MethodTypeDesc invokeMethodType(MethodTypeDesc methodType) {
		return methodType.insertParameterTypes(methodType.parameterCount(), className());
	}

	private MethodTypeDesc lambdaMethodType(MethodTypeDesc methodType) {
		return methodType.insertParameterTypes(0, className());
	}

	private void generateTags() {
		for(var tag : module.tags()) {
			var defType = closure.resolveDefType(types.get(tag.type().funcType().index()));
			var funcType = getFuncType(defType);
			var names = module.name();
			var name = names == null ? null : names.tagNames().get(tags.size());
			var tagClassName = "Tag" + tags.size() + (name == null ? "" : "_" + NameMangling.sanitizeName(name));
			var tagGen = new TagExceptionClassGenerator(compiler, className, tagClassName, funcType);
			tags.add(new TagInfo(tagGen.realization(), funcType, defType));
			compiler.enqueueGenerator(tagGen);
		}
	}

	private void generateConstructor(
		ClassBuilder clb,
		List<Consumer<CodeBuilder>> constructorInits,
		List<ClassDesc> constructorParams
	) {
		var ctorType = MethodTypeDesc.of(CD_void, constructorParams);

		clb.withMethodBody("<init>", ctorType, ClassFile.ACC_PUBLIC, cb -> {
			cb.aload(0).invokespecial(wasmModuleClass, "<init>", MethodTypeDesc.ofDescriptor("()V"));

			for(var ctorInit : constructorInits) {
				ctorInit.accept(cb);
			}

			var startSection = module.start();
			if(startSection != null) {
				var func = funcs.get(startSection.func().index());
				var resultType = func.methodType.resultType().classDesc();
				var endResultType = resultType.nested("EndResult");

				cb.aload(0);
				cb.invokestatic(func.methodsClassName(), func.name(), func.invokeMethodType());
				cb.invokestatic(resultType, "get", MethodTypeDesc.of(endResultType, resultType), true);
			}

			cb.return_();
		});
	}

	private class FuncsClassGenerator extends WasmClassGenerator {
		FuncsClassGenerator(int methodsClassIndex) {
			super(ModuleClassGenerator.this.compiler);
			this.methodsClassIndex = methodsClassIndex;
		}

		private final int methodsClassIndex;
		private final List<Consumer<ClassBuilder>> functionCreators = new ArrayList<>();
		private byte @Nullable [] cachedBytecode = null;

		int functionCount() {
			return functionCreators.size();
		}

		@Override
		public ClassDesc className() {
			return ClassDesc.of(compiler.getOptions().javaPackage(), simpleClassName + "_Funcs_" + methodsClassIndex);
		}

		@Override
		public ClassHierarchyResolver.ClassHierarchyInfo hierarchyInfo() {
			return ClassHierarchyResolver.ClassHierarchyInfo
				.ofClass(ClassDesc.ofDescriptor(Object.class.descriptorString()));
		}

		@Override
		byte[] generateImpl() {
			if(cachedBytecode == null) {
				cachedBytecode = compiler.classFile().build(className(), clb -> {
					for(var creator : functionCreators) {
						creator.accept(clb);
					}
				});
			}
			return cachedBytecode;
		}

		void addFunction(int functionIndex, String name, MethodTypeDesc type, Func func) {
			if(FunctionSizeHeuristic.isLarge(func, type.parameterCount())) {
				functionCreators.add(clb -> {
					emitFallbackFunction(clb, functionIndex, name, type, func);
					createLambdaThunk(clb, functionIndex, name, type);
				});
			}
			else {
				functionCreators.add(clb -> {
					emitDirectFunction(clb, functionIndex, name, type, func);
					createLambdaThunk(clb, functionIndex, name, type);
				});
			}
		}

		private void emitDirectFunction(
			ClassBuilder clb,
			int functionIndex,
			String name,
			MethodTypeDesc type,
			Func func
		) {
			clb.withMethodBody(name, invokeMethodType(type), ClassFile.ACC_STATIC, cb -> {
				var funcType = closure.resolveDefType(types.get(func.type().index()));

				var returnType = getFuncType(funcType).results();

				int paramCount = type.parameterCount();
				LocalInfo[] locals = new LocalInfo[paramCount + func.locals().size()];
				int slotOffset = 0;
				for(int i = 0; i < paramCount; ++i) {
					var paramType = type.parameterType(i);
					locals[i] = new LocalInfo(slotOffset, paramType);
					emitLocalName(cb, functionIndex, i, locals[i], cb.startLabel());
					slotOffset += slotSize(paramType);
				}

				int moduleInstanceSlot = slotOffset;
				slotOffset += 1;

				for(int i = 0; i < func.locals().size(); ++i) {
					var localType = compiler.getValType(closure.resolveValType(func.locals().get(i)));
					locals[paramCount + i] = new LocalInfo(slotOffset, localType.type());

					switch(typeKind(localType.type())) {
						case BOOLEAN, BYTE, CHAR, SHORT, INT -> cb.iconst_0().istore(slotOffset);
						case LONG -> cb.lconst_0().lstore(slotOffset);
						case FLOAT -> cb.fconst_0().fstore(slotOffset);
						case DOUBLE -> cb.dconst_0().dstore(slotOffset);
						case REFERENCE -> {
							if(localType.type().equals(v128Type)) {
								loadV128(cb, V128.ZERO);
							}
							else {
								cb.aconst_null();
							}
							cb.astore(slotOffset);
						}
						case VOID -> throw new RuntimeException("Unexpected VOID elementType");
					}

					var start = cb.newLabel();
					cb.labelBinding(start);
					emitLocalName(cb, functionIndex, paramCount + i, locals[paramCount + i], start);
					slotOffset += slotSize(localType.type());
				}

				var bytecodeGen = new BytecodeGenerator(cb, locals, returnType, moduleInstanceSlot, slotOffset);
				bytecodeGen.generateFunctionBody(func.body());
			});
		}

		private void emitFallbackFunction(
			ClassBuilder clb,
			int functionIndex,
			String name,
			MethodTypeDesc type,
			Func func
		) {
			var helper = new FallbackFunctionClassGenerator(functionIndex, type, func);
			compiler.enqueueGenerator(helper);
			clb.withMethodBody(name, invokeMethodType(type), ClassFile.ACC_STATIC, cb -> {
				int moduleSlot = type.parameterList().stream().mapToInt(WasmClassGeneratorUtils::slotSize).sum();
				cb.new_(helper.className()).dup();
				int slot = 0;
				for(int i = 0; i < type.parameterCount(); ++i) {
					var paramType = type.parameterType(i);
					emitLocalName(cb, functionIndex, i, new LocalInfo(slot, paramType), cb.startLabel());
					cb.loadLocal(typeKind(paramType), slot);
					slot += slotSize(paramType);
				}
				cb.aload(moduleSlot);
				cb.invokespecial(helper.className(), "<init>", invokeMethodType(type).changeReturnType(CD_void));
				cb.invokevirtual(helper.className(), "execute", MethodTypeDesc.of(type.returnType()));
				cb.areturn();
			});
		}

		void addFunctionImport(
			int functionIndex,
			ImportModuleInfo imp,
			String localName,
			MethodTypeDesc type,
			WasmExportRealization.OfInstanceMethod exportRealization
		) {
			functionCreators.add(clb -> {
				var invokeMethodType = invokeMethodType(type);
				clb.withMethodBody(localName, invokeMethodType, ClassFile.ACC_STATIC, cb -> {
					int slotOffset = 0;
					for(int i = 0; i < type.parameterCount(); ++i) {
						var paramType = type.parameterType(i);
						emitLocalName(cb, functionIndex, i, new LocalInfo(slotOffset, paramType), cb.startLabel());
						slotOffset += slotSize(paramType);
					}

					cb.aload(slotOffset);
					cb.getfield(className, imp.fieldName, imp.realization().classDesc());

					slotOffset = 0;
					for(int i = 0; i < type.parameterCount(); ++i) {
						var paramType = type.parameterType(i);
						cb.loadLocal(typeKind(paramType), slotOffset);
						slotOffset += slotSize(paramType);
					}

					cb.invokevirtual(
						imp.realization().classDesc(),
						exportRealization.methodName(),
						exportRealization.methodType()
					);
					cb.areturn();
				});

				createLambdaThunk(clb, functionIndex, localName, type);
			});
		}

		private void createLambdaThunk(ClassBuilder clb, int functionIndex, String methodName, MethodTypeDesc type) {
			if(type.parameterCount() == 0) {
				return;
			}

			var lambdaMethodType = lambdaMethodType(type);
			clb.withMethodBody(methodName, lambdaMethodType, ClassFile.ACC_STATIC, cb -> {
				int slotOffset = 1;
				for(int i = 0; i < type.parameterCount(); ++i) {
					var paramType = type.parameterType(i);
					emitLocalName(cb, functionIndex, i, new LocalInfo(slotOffset, paramType), cb.startLabel());
					cb.loadLocal(typeKind(paramType), slotOffset);
					slotOffset += slotSize(paramType);
				}

				cb.aload(0);

				cb.invokestatic(className(), methodName, invokeMethodType(type));
				cb.areturn();
			});
		}
	}

	private class ElemsInitClassGenerator extends WasmClassGenerator {
		ElemsInitClassGenerator(
			int initClassIndex, RefType elemType, ClassDesc elemClassType, int elemStartIndex, List<Expr> elems
		) {
			super(ModuleClassGenerator.this.compiler);
			this.initClassIndex = initClassIndex;
			this.elemType = elemType;
			this.elemClassType = elemClassType;
			this.elemStartIndex = elemStartIndex;
			this.elems = elems;
		}

		private final int initClassIndex;
		private final RefType elemType;
		private final ClassDesc elemClassType;
		private final int elemStartIndex;
		private final List<Expr> elems;
		private byte @Nullable [] cachedBytecode = null;

		@Override
		public ClassDesc className() {
			return ClassDesc.of(compiler.getOptions().javaPackage(), simpleClassName + "_ElemsInit_" + initClassIndex);
		}

		@Override
		public ClassHierarchyResolver.ClassHierarchyInfo hierarchyInfo() {
			return ClassHierarchyResolver.ClassHierarchyInfo
				.ofClass(ClassDesc.ofDescriptor(Object.class.descriptorString()));
		}

		static final String INIT_METHOD_NAME = "init";

		MethodTypeDesc initMethodType() {
			return MethodTypeDesc.of(CD_void, ModuleClassGenerator.this.className(), elemClassType.arrayType());
		}

		@Override
		byte[] generateImpl() {
			if(cachedBytecode == null) {
				cachedBytecode = compiler.classFile().build(className(), clb -> {
					clb.withMethodBody(INIT_METHOD_NAME, initMethodType(), ClassFile.ACC_STATIC, cb -> {
						for(int i = 0; i < elems.size(); ++i) {
							cb.aload(1);
							cb.loadConstant(elemStartIndex + i);

							var bytecodeGen = new BytecodeGenerator(
								cb,
								new LocalInfo[]{},
								new ResultType(ImmutableList.of(elemType)),
								0,
								2
							);
							bytecodeGen.generateInstructionBlock(elems.get(i));

							cb.aastore();
						}

						cb.return_();
					});
				});
			}

			return cachedBytecode;
		}
	}

	private abstract class BytecodeGeneratorBase {
		BytecodeGeneratorBase(CodeBuilder cb, ResultType returnType, int tempVarSlot) {
			this.cb = cb;
			this.returnType = returnType;
			this.tempVarSlot = tempVarSlot;
		}

		protected final CodeBuilder cb;
		protected final ResultType returnType;

		protected abstract ClassDesc localType(int index);

		// Storage hooks leave Wasm stack tracking to the shared instruction emitter.
		// Loads push one value; stores consume one value and preserve the lower stack.
		protected abstract void loadLocal(int index);

		protected abstract void storeLocal(int index);

		protected abstract void loadModuleInstance();

		// Scratch indices are reusable storage identifiers, with category-2 values
		// reserving two indices. The direct implementation maps them to JVM slots.
		protected abstract void loadScratch(TypeKind type, int index);

		protected abstract void storeScratch(TypeKind type, int index);

		protected abstract void emitResultReturn();

		protected int tempVarSlot;
		protected static ClassDesc stackType(ClassDesc type) {
			return type.isPrimitive() ? typeKind(type).asLoadable().upperBound() : type;
		}

		protected final List<ClassDesc> stackTypes = new ArrayList<>();
		protected boolean isUnreachable = false;

		protected void generateInstruction(Instr insn) {
			switch(insn) {
				case ControlInstr controlInstr -> generateControlInstr(controlInstr);
				case MemoryInstr memoryInstr -> generateMemoryInstr(memoryInstr);
				case NumericInstr numericInstr -> generateNumericInstr(numericInstr);
				case ParametricInstr parametricInstr -> generateParametricInstr(parametricInstr);
				case ReferenceInstr referenceInstr -> generateReferenceInstr(referenceInstr);
				case TableInstr tableInstr -> generateTableInstr(tableInstr);
				case VariableInstr variableInstr -> generateVariableInstr(variableInstr);
				case VectorInstr vectorInstr -> generateVectorInstr(vectorInstr);
			}
		}

		protected abstract void generateControlInstr(ControlInstr instr);

		private void generateMemoryInstr(MemoryInstr instr) {
			switch(instr) {
				case MemoryInstr.Inn_Load(var numSize, var memArg) -> {
					doLoad(intSizeDesc(numSize), memArg);
				}
				case MemoryInstr.Inn_Store(var numSize, var memArg) -> {
					doStore(intSizeDesc(numSize), memArg);
				}
				case MemoryInstr.Inn_Load8_S(var numSize, var memArg) -> {
					doLoad(CD_byte, memArg);
					switch(numSize) {
						case _32 -> {
						}
						case _64 -> {
							cb.i2l();
							stackTypes.removeLast();
							stackTypes.add(CD_long);
						}
					}
				}
				case MemoryInstr.Inn_Load8_U(var numSize, var memArg) -> {
					doLoad(CD_byte, memArg);
					switch(numSize) {
						case _32 -> {
							cb.invokestatic(CD_Byte, "toUnsignedInt", MethodTypeDesc.ofDescriptor("(B)I"));
						}
						case _64 -> {
							cb.invokestatic(CD_Byte, "toUnsignedLong", MethodTypeDesc.ofDescriptor("(B)J"));
							stackTypes.removeLast();
							stackTypes.add(CD_long);
						}
					}
				}
				case MemoryInstr.Inn_Store8(var numSize, var memArg) -> {
					switch(numSize) {
						case _32 -> {
						}
						case _64 -> {
							cb.l2i();
							stackTypes.removeLast();
							stackTypes.add(CD_int);
						}
					}
					doStore(CD_byte, memArg);
				}
				case MemoryInstr.Inn_Load16_S(var numSize, var memArg) -> {
					doLoad(CD_short, memArg);
					switch(numSize) {
						case _32 -> {
						}
						case _64 -> {
							cb.i2l();
							stackTypes.removeLast();
							stackTypes.add(CD_long);
						}
					}
				}
				case MemoryInstr.Inn_Load16_U(var numSize, var memArg) -> {
					doLoad(CD_short, memArg);
					switch(numSize) {
						case _32 -> {
							cb.invokestatic(CD_Short, "toUnsignedInt", MethodTypeDesc.ofDescriptor("(S)I"));
						}
						case _64 -> {
							cb.invokestatic(CD_Short, "toUnsignedLong", MethodTypeDesc.ofDescriptor("(S)J"));
							stackTypes.removeLast();
							stackTypes.add(CD_long);
						}
					}
				}
				case MemoryInstr.Inn_Store16(var numSize, var memArg) -> {
					switch(numSize) {
						case _32 -> {
						}
						case _64 -> {
							cb.l2i();
							stackTypes.removeLast();
							stackTypes.add(CD_int);
						}
					}
					doStore(CD_short, memArg);
				}

				case MemoryInstr.I64_Load32_S(var memArg) -> {
					doLoad(CD_int, memArg);
					cb.i2l();
					stackTypes.removeLast();
					stackTypes.add(CD_long);
				}
				case MemoryInstr.I64_Load32_U(var memArg) -> {
					doLoad(CD_int, memArg);
					cb.invokestatic(CD_Integer, "toUnsignedLong", MethodTypeDesc.ofDescriptor("(I)J"));
					stackTypes.removeLast();
					stackTypes.add(CD_long);
				}
				case MemoryInstr.I64_Store32(var memArg) -> {
					stackTypes.removeLast();
					stackTypes.add(CD_int);
					cb.l2i();
					doStore(CD_int, memArg);
				}

				case MemoryInstr.Fnn_Load(var numSize, var memArg) -> {
					doLoad(floatSizeDesc(numSize), memArg);
				}
				case MemoryInstr.Fnn_Store(var numSize, var memArg) -> {
					doStore(floatSizeDesc(numSize), memArg);
				}

				case MemoryInstr.Memory_Size(var memIdx) -> {
					var mem = mems.get(memIdx.index());
					var wasmMemory = mem.memoryClass();

					loadMem(memIdx);

					cb.invokevirtual(wasmMemory, "pageSize", MethodTypeDesc.of(addrDesc(mem.memType.addrType())));

					switch(mem.memType.addrType()) {
						case I32 -> {
							stackTypes.add(CD_int);
						}
						case I64 -> stackTypes.add(CD_long);
					}
				}
				case MemoryInstr.Memory_Grow(var memIdx) -> {
					var mem = mems.get(memIdx.index());
					var wasmMemory = mem.memoryClass();
					var at = addrDesc(mem.memType.addrType());

					loadMem(memIdx);

					cb.invokestatic(wasmMemory, "grow", MethodTypeDesc.of(at, at, wasmMemory));
				}
				case MemoryInstr.Memory_Fill(var memIdx) -> {
					var mem = mems.get(memIdx.index());
					var wasmMemory = mem.memoryClass();
					var at = addrDesc(mem.memType.addrType());
					loadModuleInstance();
					cb.getfield(className, mem.fieldName, wasmMemory);
					cb.invokestatic(wasmMemory, "fill", MethodTypeDesc.of(CD_void, at, CD_byte, at, wasmMemory));
					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.removeLast();
				}
				case MemoryInstr.Memory_Copy(var destMemIdx, var srcMemIdx) -> {
					var dstMem = mems.get(destMemIdx.index());
					var srcMem = mems.get(srcMemIdx.index());
					var dat = addrDesc(dstMem.memType.addrType());
					var sat = addrDesc(srcMem.memType.addrType());
					var minAt = addrDesc(switch(dstMem.memType.addrType()) {
						case I32 -> AddrType.I32;
						case I64 -> srcMem.memType.addrType();
					});

					loadModuleInstance();
					cb.getfield(className, dstMem.fieldName, dstMem.memoryClass());
					loadModuleInstance();
					cb.getfield(className, srcMem.fieldName, srcMem.memoryClass());
					cb.invokestatic(
						dstMem.memoryClass(),
						"copy",
						MethodTypeDesc.of(CD_void, dat, sat, minAt, dstMem.memoryClass(), srcMem.memoryClass())
					);
					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.removeLast();
				}
				case MemoryInstr.Memory_Init(var memIdx, var dataIdx) -> {
					var mem = mems.get(memIdx.index());
					var wasmMemory = mem.memoryClass();
					var at = addrDesc(mem.memType.addrType());

					loadModuleInstance();
					cb.getfield(className, "data" + dataIdx.index(), CD_byte.arrayType());

					loadModuleInstance();
					cb.getfield(className, mem.fieldName, wasmMemory);

					cb.invokestatic(
						wasmMemory,
						"copyFromArray",
						MethodTypeDesc.of(CD_void, at, CD_int, CD_int, CD_byte.arrayType(), wasmMemory)
					);
					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.removeLast();
				}
				case MemoryInstr.Data_Drop(var dataIdx) -> {
					loadModuleInstance();
					cb.iconst_0();
					cb.newarray(TypeKind.BYTE);
					cb.putfield(className, "data" + dataIdx.index(), CD_byte.arrayType());
				}

				case MemoryInstr.V128_Load(var memArg) -> {
					doLoad(v128Type, memArg);
				}
				case MemoryInstr.V128_Store(var memArg) -> {
					doStore(v128Type, memArg);
				}
				case MemoryInstr.V128_Load8_Lane(var memArg, var laneIdx) -> {
					storeScratch(TypeKind.REFERENCE, tempVarSlot);
					stackTypes.removeLast();
					doLoad(CD_byte, memArg);
					stackTypes.removeLast();
					loadScratch(TypeKind.REFERENCE, tempVarSlot);
					cb.swap();
					cb.loadConstant(laneIdx);
					cb.invokevirtual(v128Type, "replaceLane8", MethodTypeDesc.of(v128Type, CD_byte, CD_int));
					stackTypes.add(v128Type);
				}
				case MemoryInstr.V128_Load16_Lane(var memArg, var laneIdx) -> {
					storeScratch(TypeKind.REFERENCE, tempVarSlot);
					stackTypes.removeLast();
					doLoad(CD_short, memArg);
					stackTypes.removeLast();
					loadScratch(TypeKind.REFERENCE, tempVarSlot);
					cb.swap();
					cb.loadConstant(laneIdx);
					cb.invokevirtual(v128Type, "replaceLane16", MethodTypeDesc.of(v128Type, CD_short, CD_int));
					stackTypes.add(v128Type);
				}
				case MemoryInstr.V128_Load32_Lane(var memArg, var laneIdx) -> {
					storeScratch(TypeKind.REFERENCE, tempVarSlot);
					stackTypes.removeLast();
					doLoad(CD_int, memArg);
					stackTypes.removeLast();
					loadScratch(TypeKind.REFERENCE, tempVarSlot);
					cb.swap();
					cb.loadConstant(laneIdx);
					cb.invokevirtual(v128Type, "replaceLane32", MethodTypeDesc.of(v128Type, CD_int, CD_int));
					stackTypes.add(v128Type);
				}
				case MemoryInstr.V128_Load64_Lane(var memArg, var laneIdx) -> {
					storeScratch(TypeKind.REFERENCE, tempVarSlot);
					stackTypes.removeLast();
					doLoad(CD_long, memArg);
					stackTypes.removeLast();
					loadScratch(TypeKind.REFERENCE, tempVarSlot);
					cb.dup_x2();
					cb.pop();
					cb.loadConstant(laneIdx);
					cb.invokevirtual(v128Type, "replaceLane64", MethodTypeDesc.of(v128Type, CD_long, CD_int));
					stackTypes.add(v128Type);
				}
				case MemoryInstr.V128_Store8_Lane(var memArg, var laneIdx) -> {
					cb.loadConstant(laneIdx);
					cb.invokevirtual(v128Type, "extractLane8", MethodTypeDesc.of(CD_byte, CD_int));
					doStore(CD_byte, memArg);
				}
				case MemoryInstr.V128_Store16_Lane(var memArg, var laneIdx) -> {
					cb.loadConstant(laneIdx);
					cb.invokevirtual(v128Type, "extractLane16", MethodTypeDesc.of(CD_short, CD_int));
					doStore(CD_short, memArg);
				}
				case MemoryInstr.V128_Store32_Lane(var memArg, var laneIdx) -> {
					cb.loadConstant(laneIdx);
					cb.invokevirtual(v128Type, "extractLane32", MethodTypeDesc.of(CD_int, CD_int));
					doStore(CD_int, memArg);
				}
				case MemoryInstr.V128_Store64_Lane(var memArg, var laneIdx) -> {
					cb.loadConstant(laneIdx);
					cb.invokevirtual(v128Type, "extractLane64", MethodTypeDesc.of(CD_long, CD_int));
					doStore(CD_long, memArg);
				}
				case MemoryInstr.V128_Load32_Zero(var memArg) -> {
					doLoad(CD_int, memArg);
					cb.invokestatic(v128Type, "ofIntZero", MethodTypeDesc.of(v128Type, CD_int));
					stackTypes.removeLast();
					stackTypes.add(v128Type);
				}
				case MemoryInstr.V128_Load64_Zero(var memArg) -> {
					doLoad(CD_long, memArg);
					cb.invokestatic(v128Type, "ofLongZero", MethodTypeDesc.of(v128Type, CD_long));
					stackTypes.removeLast();
					stackTypes.add(v128Type);
				}
				case MemoryInstr.V128_Load8x8_S(var memArg) -> {
					doLoad(CD_long, memArg);
					cb.invokestatic(v128Type, "of8x8S", MethodTypeDesc.of(v128Type, CD_long));
					stackTypes.removeLast();
					stackTypes.add(v128Type);
				}
				case MemoryInstr.V128_Load8x8_U(var memArg) -> {
					doLoad(CD_long, memArg);
					cb.invokestatic(v128Type, "of8x8U", MethodTypeDesc.of(v128Type, CD_long));
					stackTypes.removeLast();
					stackTypes.add(v128Type);
				}
				case MemoryInstr.V128_Load16x4_S(var memArg) -> {
					doLoad(CD_long, memArg);
					cb.invokestatic(v128Type, "of16x4S", MethodTypeDesc.of(v128Type, CD_long));
					stackTypes.removeLast();
					stackTypes.add(v128Type);
				}
				case MemoryInstr.V128_Load16x4_U(var memArg) -> {
					doLoad(CD_long, memArg);
					cb.invokestatic(v128Type, "of16x4U", MethodTypeDesc.of(v128Type, CD_long));
					stackTypes.removeLast();
					stackTypes.add(v128Type);
				}
				case MemoryInstr.V128_Load32x2_S(var memArg) -> {
					doLoad(CD_long, memArg);
					cb.invokestatic(v128Type, "of32x2S", MethodTypeDesc.of(v128Type, CD_long));
					stackTypes.removeLast();
					stackTypes.add(v128Type);
				}
				case MemoryInstr.V128_Load32x2_U(var memArg) -> {
					doLoad(CD_long, memArg);
					cb.invokestatic(v128Type, "of32x2U", MethodTypeDesc.of(v128Type, CD_long));
					stackTypes.removeLast();
					stackTypes.add(v128Type);
				}
				case MemoryInstr.V128_Load8_Splat(var memArg) -> {
					doLoad(CD_byte, memArg);
					cb.invokestatic(v128Type, "splat8", MethodTypeDesc.of(v128Type, CD_byte));
					stackTypes.removeLast();
					stackTypes.add(v128Type);
				}
				case MemoryInstr.V128_Load16_Splat(var memArg) -> {
					doLoad(CD_short, memArg);
					cb.invokestatic(v128Type, "splat16", MethodTypeDesc.of(v128Type, CD_short));
					stackTypes.removeLast();
					stackTypes.add(v128Type);
				}
				case MemoryInstr.V128_Load32_Splat(var memArg) -> {
					doLoad(CD_int, memArg);
					cb.invokestatic(v128Type, "splat32", MethodTypeDesc.of(v128Type, CD_int));
					stackTypes.removeLast();
					stackTypes.add(v128Type);
				}
				case MemoryInstr.V128_Load64_Splat(var memArg) -> {
					doLoad(CD_long, memArg);
					cb.invokestatic(v128Type, "splat64", MethodTypeDesc.of(v128Type, CD_long));
					stackTypes.removeLast();
					stackTypes.add(v128Type);
				}
			}
		}

		private void generateNumericInstr(NumericInstr instr) {
			switch(instr) {
				case NumericInstr.I32_Const(var value) -> {
					cb.loadConstant(value);
					stackTypes.add(CD_int);
				}
				case NumericInstr.I64_Const(var value) -> {
					if(value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
						cb.loadConstant((int)value);
						cb.i2l();
					}
					else {
						cb.loadConstant(value);
					}
					stackTypes.add(CD_long);
				}
				case NumericInstr.F32_Const(var value) -> {
					if(Float.isNaN(value) && Float.floatToRawIntBits(value) != Float.floatToRawIntBits(Float.NaN)) {
						cb.loadConstant(Float.floatToRawIntBits(value));
						cb.invokestatic(CD_Float, "intBitsToFloat", MethodTypeDesc.of(CD_float, CD_int));
					}
					else {
						cb.loadConstant(value);
					}
					stackTypes.add(CD_float);
				}
				case NumericInstr.F64_Const(var value) -> {
					if(Double.isNaN(value)
						&& Double.doubleToRawLongBits(value) != Double.doubleToRawLongBits(Double.NaN)) {
						cb.loadConstant(Double.doubleToRawLongBits(value));
						cb.invokestatic(CD_Double, "longBitsToDouble", MethodTypeDesc.of(CD_double, CD_long));
					}
					else {
						cb.loadConstant(value);
					}
					stackTypes.add(CD_double);
				}
				case NumericInstr.Inn_IUnOp(var size, var op) -> {
					var methodName = switch(op) {
						case CLZ -> "numberOfLeadingZeros";
						case CTZ -> "numberOfTrailingZeros";
						case POPCNT -> "bitCount";
					};

					switch(size) {
						case _32 -> cb.invokestatic(CD_Integer, methodName, MethodTypeDesc.ofDescriptor("(I)I"));
						case _64 -> cb.invokestatic(CD_Long, methodName, MethodTypeDesc.ofDescriptor("(J)I")).i2l();
					}
				}
				case NumericInstr.Fnn_FUnOp op -> {
					var size = op.size();
					var descriptor = switch(size) {
						case _32 -> MethodTypeDesc.ofDescriptor("(F)F");
						case _64 -> MethodTypeDesc.ofDescriptor("(D)D");
					};

					var nameSuffix = switch(size) {
						case _32 -> "F32";
						case _64 -> "F64";
					};

					switch(op.op()) {
						case ABS -> cb.invokestatic(mathClass, "abs", descriptor);
						case NEG -> {
							switch(size) {
								case _32 -> cb.fneg();
								case _64 -> cb.dneg();
							}
						}
						case SQRT -> {
							switch(size) {
								case _32 -> cb.f2d();
								case _64 -> {
								}
							}
							cb.invokestatic(mathClass, "sqrt", MethodTypeDesc.ofDescriptor("(D)D"));
							switch(size) {
								case _32 -> cb.d2f();
								case _64 -> {
								}
							}
						}
						case CEIL -> cb.invokestatic(utilClass, "ceil" + nameSuffix, descriptor);
						case FLOOR -> cb.invokestatic(utilClass, "floor" + nameSuffix, descriptor);
						case TRUNC -> cb.invokestatic(utilClass, "trunc" + nameSuffix, descriptor);
						case NEAREST -> cb.invokestatic(utilClass, "nearest" + nameSuffix, descriptor);
					}
				}
				case NumericInstr.I64_Add128() -> {
					int aLow = tempVarSlot;
					int aHigh = tempVarSlot + 2;
					int bLow = tempVarSlot + 4;
					int bHigh = tempVarSlot + 6;
					storeScratch(TypeKind.LONG, bHigh);
					storeScratch(TypeKind.LONG, bLow);
					storeScratch(TypeKind.LONG, aHigh);
					storeScratch(TypeKind.LONG, aLow);

					loadScratch(TypeKind.LONG, aLow);
					loadScratch(TypeKind.LONG, bLow);
					cb.ladd();

					loadScratch(TypeKind.LONG, aHigh);
					loadScratch(TypeKind.LONG, bHigh);
					cb.ladd();
					loadScratch(TypeKind.LONG, aLow);
					loadScratch(TypeKind.LONG, bLow);
					cb.ladd();
					loadScratch(TypeKind.LONG, aLow);
					cb.invokestatic(CD_Long, "compareUnsigned", MethodTypeDesc.of(CD_int, CD_long, CD_long));
					cb.loadConstant(31);
					cb.iushr();
					cb.i2l();
					cb.ladd();

					stackTypes.removeLast();
					stackTypes.removeLast();
				}
				case NumericInstr.I64_Sub128() -> {
					int aLow = tempVarSlot;
					int aHigh = tempVarSlot + 2;
					int bLow = tempVarSlot + 4;
					int bHigh = tempVarSlot + 6;

					storeScratch(TypeKind.LONG, bHigh);
					storeScratch(TypeKind.LONG, bLow);
					storeScratch(TypeKind.LONG, aHigh);
					storeScratch(TypeKind.LONG, aLow);

					loadScratch(TypeKind.LONG, aLow);
					loadScratch(TypeKind.LONG, bLow);
					cb.lsub();

					loadScratch(TypeKind.LONG, aHigh);
					loadScratch(TypeKind.LONG, bHigh);
					cb.lsub();
					loadScratch(TypeKind.LONG, aLow);
					loadScratch(TypeKind.LONG, bLow);
					cb.invokestatic(CD_Long, "compareUnsigned", MethodTypeDesc.of(CD_int, CD_long, CD_long));
					cb.loadConstant(31);
					cb.iushr();
					cb.i2l();
					cb.lsub();

					stackTypes.removeLast();
					stackTypes.removeLast();
				}
				case NumericInstr.I64_Mul_Wide_S() -> {
					int a = tempVarSlot;
					int b = tempVarSlot + 2;
					storeScratch(TypeKind.LONG, b);
					storeScratch(TypeKind.LONG, a);

					loadScratch(TypeKind.LONG, a);
					loadScratch(TypeKind.LONG, b);
					cb.lmul();

					loadScratch(TypeKind.LONG, a);
					loadScratch(TypeKind.LONG, b);
					cb.invokestatic(mathClass, "multiplyHigh", MethodTypeDesc.of(CD_long, CD_long, CD_long));
				}
				case NumericInstr.I64_Mul_Wide_U() -> {
					int a = tempVarSlot;
					int b = tempVarSlot + 2;
					storeScratch(TypeKind.LONG, b);
					storeScratch(TypeKind.LONG, a);

					loadScratch(TypeKind.LONG, a);
					loadScratch(TypeKind.LONG, b);
					cb.lmul();

					loadScratch(TypeKind.LONG, a);
					loadScratch(TypeKind.LONG, b);
					cb.invokestatic(mathClass, "unsignedMultiplyHigh", MethodTypeDesc.of(CD_long, CD_long, CD_long));
				}

				case NumericInstr.Inn_IBinOp op -> {
					var size = op.size();
					var descriptor = switch(size) {
						case _32 -> MethodTypeDesc.ofDescriptor("(II)I");
						case _64 -> MethodTypeDesc.ofDescriptor("(JJ)J");
					};

					switch(op.op()) {
						case ADD -> {
							switch(size) {
								case _32 -> cb.iadd();
								case _64 -> cb.ladd();
							}
						}
						case SUB -> {
							switch(size) {
								case _32 -> cb.isub();
								case _64 -> cb.lsub();
							}
						}
						case MUL -> {
							switch(size) {
								case _32 -> cb.imul();
								case _64 -> cb.lmul();
							}
						}
						case DIV_U -> {
							switch(size) {
								case _32 -> cb.invokestatic(CD_Integer, "divideUnsigned", descriptor);
								case _64 -> cb.invokestatic(CD_Long, "divideUnsigned", descriptor);
							}
						}
						case DIV_S -> {
							switch(size) {
								case _32 -> cb.invokestatic(utilClass, "divideS32", descriptor);
								case _64 -> cb.invokestatic(utilClass, "divideS64", descriptor);
							}
						}
						case REM_U -> {
							switch(size) {
								case _32 -> cb.invokestatic(CD_Integer, "remainderUnsigned", descriptor);
								case _64 -> cb.invokestatic(CD_Long, "remainderUnsigned", descriptor);
							}
						}
						case REM_S -> {
							switch(size) {
								case _32 -> cb.irem();
								case _64 -> cb.lrem();
							}
						}
						case AND -> {
							switch(size) {
								case _32 -> cb.iand();
								case _64 -> cb.land();
							}
						}
						case OR -> {
							switch(size) {
								case _32 -> cb.ior();
								case _64 -> cb.lor();
							}
						}
						case XOR -> {
							switch(size) {
								case _32 -> cb.ixor();
								case _64 -> cb.lxor();
							}
						}
						case SHL -> {
							switch(size) {
								case _32 -> cb.ishl();
								case _64 -> cb.l2i().lshl();
							}
						}
						case SHR_U -> {
							switch(size) {
								case _32 -> cb.iushr();
								case _64 -> cb.l2i().lushr();
							}
						}
						case SHR_S -> {
							switch(size) {
								case _32 -> cb.ishr();
								case _64 -> cb.l2i().lshr();
							}
						}
						case ROTL -> {
							switch(size) {
								case _32 -> cb.invokestatic(CD_Integer, "rotateLeft", descriptor);
								case _64 ->
									cb.l2i().invokestatic(CD_Long, "rotateLeft", MethodTypeDesc.ofDescriptor("(JI)J"));
							}
						}
						case ROTR -> {
							switch(size) {
								case _32 -> cb.invokestatic(CD_Integer, "rotateRight", descriptor);
								case _64 ->
									cb.l2i().invokestatic(CD_Long, "rotateRight", MethodTypeDesc.ofDescriptor("(JI)J"));
							}
						}
					}

					stackTypes.removeLast();
				}
				case NumericInstr.Fnn_FBinOp op -> {
					var size = op.size();
					var descriptor = switch(size) {
						case _32 -> MethodTypeDesc.ofDescriptor("(FF)F");
						case _64 -> MethodTypeDesc.ofDescriptor("(DD)D");
					};

					switch(op.op()) {
						case ADD -> {
							switch(size) {
								case _32 -> cb.fadd();
								case _64 -> cb.dadd();
							}
						}
						case SUB -> {
							switch(size) {
								case _32 -> cb.fsub();
								case _64 -> cb.dsub();
							}
						}
						case MUL -> {
							switch(size) {
								case _32 -> cb.fmul();
								case _64 -> cb.dmul();
							}
						}
						case DIV -> {
							switch(size) {
								case _32 -> cb.fdiv();
								case _64 -> cb.ddiv();
							}
						}
						case MIN -> cb.invokestatic(utilClass, "min", descriptor);
						case MAX -> cb.invokestatic(utilClass, "max", descriptor);
						case COPYSIGN -> cb.invokestatic(mathClass, "copySign", descriptor);
					}

					stackTypes.removeLast();
				}
				case NumericInstr.Inn_ITestOp op -> {
					var descriptor = switch(op.size()) {
						case _32 -> MethodTypeDesc.ofDescriptor("(I)Z");
						case _64 -> MethodTypeDesc.ofDescriptor("(J)Z");
					};

					switch(op.op()) {
						case EQZ -> cb.invokestatic(utilClass, "equalsZero", descriptor);
					}

					stackTypes.removeLast();
					stackTypes.add(CD_int);
				}
				case NumericInstr.Inn_IRelOp(var size, var op) -> {
					var descriptor = switch(size) {
						case _32 -> MethodTypeDesc.ofDescriptor("(II)Z");
						case _64 -> MethodTypeDesc.ofDescriptor("(JJ)Z");
					};

					var methodName = switch(op) {
						case EQ -> "numEquals";
						case NE -> "numNotEquals";
						case LT_U -> "numLessThanUnsigned";
						case LT_S -> "numLessThanSigned";
						case GT_U -> "numGreaterThanUnsigned";
						case GT_S -> "numGreaterThanSigned";
						case LE_U -> "numLessThanOrEqualUnsigned";
						case LE_S -> "numLessThanOrEqualSigned";
						case GE_U -> "numGreaterThanOrEqualUnsigned";
						case GE_S -> "numGreaterThanOrEqualSigned";
					};

					cb.invokestatic(utilClass, methodName, descriptor);

					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.add(CD_int);
				}
				case NumericInstr.Fnn_FRelOp(var size, var op) -> {
					var descriptor = switch(size) {
						case _32 -> MethodTypeDesc.ofDescriptor("(FF)Z");
						case _64 -> MethodTypeDesc.ofDescriptor("(DD)Z");
					};

					var methodName = switch(op) {
						case EQ -> "numEquals";
						case NE -> "numNotEquals";
						case LT -> "numLessThan";
						case GT -> "numGreaterThan";
						case LE -> "numLessThanOrEqual";
						case GE -> "numGreaterThanOrEqual";
					};

					cb.invokestatic(utilClass, methodName, descriptor);

					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.add(CD_int);
				}

				case NumericInstr.Inn_Extend8_S(var size) -> {
					switch(size) {
						case _32 -> cb.i2b();
						case _64 -> cb.l2i().i2b().i2l();
					}
				}

				case NumericInstr.Inn_Extend16_S(var size) -> {
					switch(size) {
						case _32 -> cb.i2s();
						case _64 -> cb.l2i().i2s().i2l();
					}
				}
				case NumericInstr.I64_Extend32_S() -> cb.l2i().i2l();
				case NumericInstr.I32_Wrap_I64() -> {
					cb.l2i();
					stackTypes.removeLast();
					stackTypes.add(CD_int);
				}
				case NumericInstr.I64_Extend_I32_S() -> {
					cb.i2l();
					stackTypes.removeLast();
					stackTypes.add(CD_long);
				}
				case NumericInstr.I64_Extend_I32_U() -> {
					cb.invokestatic(CD_Integer, "toUnsignedLong", MethodTypeDesc.ofDescriptor("(I)J"));
					stackTypes.removeLast();
					stackTypes.add(CD_long);
				}
				case NumericInstr.Inn_Trunc_Fmm_S trunc -> {
					stackTypes.removeLast();

					switch(trunc.floatSize()) {
						case _32 -> cb.f2d();
						case _64 -> {
						}
					}

					switch(trunc.intSize()) {
						case _32 -> cb.invokestatic(utilClass, "truncF64ToS32", MethodTypeDesc.ofDescriptor("(D)I"));
						case _64 -> cb.invokestatic(utilClass, "truncF64ToS64", MethodTypeDesc.ofDescriptor("(D)J"));
					}

					stackTypes.add(stackType(intSizeDesc(trunc.intSize())));
				}
				case NumericInstr.Inn_Trunc_Fmm_U trunc -> {
					stackTypes.removeLast();

					switch(trunc.floatSize()) {
						case _32 -> cb.f2d();
						case _64 -> {
						}
					}

					switch(trunc.intSize()) {
						case _32 -> cb.invokestatic(utilClass, "truncF64ToU32", MethodTypeDesc.ofDescriptor("(D)I"));
						case _64 -> cb.invokestatic(utilClass, "truncF64ToU64", MethodTypeDesc.ofDescriptor("(D)J"));
					}

					stackTypes.add(stackType(intSizeDesc(trunc.intSize())));
				}
				case NumericInstr.Inn_Trunc_Sat_Fmm_S(var intSize, var floatSize) -> {
					stackTypes.removeLast();
					switch(floatSize) {
						case _32 -> {
							switch(intSize) {
								case _32 -> cb.f2i();
								case _64 -> cb.f2l();
							}
						}
						case _64 -> {
							switch(intSize) {
								case _32 -> cb.d2i();
								case _64 -> cb.d2l();
							}
						}
					}

					stackTypes.add(stackType(intSizeDesc(intSize)));
				}
				case NumericInstr.Inn_Trunc_Sat_Fmm_U(var intSize, var floatSize) -> {
					stackTypes.removeLast();

					switch(floatSize) {
						case _32 -> {
							switch(intSize) {
								case _32 ->
									cb.invokestatic(utilClass, "truncSatF32U32", MethodTypeDesc.ofDescriptor("(F)I"));
								case _64 ->
									cb.invokestatic(utilClass, "truncSatF32U64", MethodTypeDesc.ofDescriptor("(F)J"));
							}
						}
						case _64 -> {
							switch(intSize) {
								case _32 ->
									cb.invokestatic(utilClass, "truncSatF64U32", MethodTypeDesc.ofDescriptor("(D)I"));
								case _64 ->
									cb.invokestatic(utilClass, "truncSatF64U64", MethodTypeDesc.ofDescriptor("(D)J"));
							}
						}
					}

					stackTypes.add(stackType(intSizeDesc(intSize)));
				}
				case NumericInstr.F32_Demote_F64() -> {
					cb.d2f();
					stackTypes.set(stackTypes.size() - 1, CD_float);
				}
				case NumericInstr.F64_Promote_F32() -> {
					cb.f2d();
					stackTypes.set(stackTypes.size() - 1, CD_double);
				}
				case NumericInstr.Fnn_Convert_Imm_S(var floatSize, var intSize) -> {
					stackTypes.removeLast();
					switch(floatSize) {
						case _32 -> {
							switch(intSize) {
								case _32 -> cb.i2f();
								case _64 -> cb.l2f();
							};
							stackTypes.add(CD_float);
						}
						case _64 -> {
							switch(intSize) {
								case _32 -> cb.i2d();
								case _64 -> cb.l2d();
							};
							stackTypes.add(CD_double);
						}
					}
				}
				case NumericInstr.Fnn_Convert_Imm_U(var floatSize, var intSize) -> {
					stackTypes.removeLast();
					switch(floatSize) {
						case _32 -> {
							switch(intSize) {
								case _32 -> {
									cb.invokestatic(CD_Integer, "toUnsignedLong", MethodTypeDesc.ofDescriptor("(I)J"));
									cb.l2f();
								}
								case _64 -> {
									cb.invokestatic(utilClass, "u64ToF32", MethodTypeDesc.ofDescriptor("(J)F"));
								}
							};
							stackTypes.add(CD_float);
						}
						case _64 -> {
							switch(intSize) {
								case _32 -> {
									cb.invokestatic(CD_Integer, "toUnsignedLong", MethodTypeDesc.ofDescriptor("(I)J"));
									cb.l2d();
								}
								case _64 -> {
									cb.invokestatic(utilClass, "u64ToF64", MethodTypeDesc.ofDescriptor("(J)D"));
								}
							};
							stackTypes.add(CD_double);
						}
					}
				}
				case NumericInstr.Fnn_Reinterpret_Inn(var numSize) -> {
					switch(numSize) {
						case _32 -> cb.invokestatic(CD_Float, "intBitsToFloat", MethodTypeDesc.of(CD_float, CD_int));
						case _64 ->
							cb.invokestatic(CD_Double, "longBitsToDouble", MethodTypeDesc.of(CD_double, CD_long));
					}
					stackTypes.set(stackTypes.size() - 1, floatSizeDesc(Objects.requireNonNull(numSize)));
				}
				case NumericInstr.Inn_Reinterpret_Fnn(var numSize) -> {
					switch(numSize) {
						case _32 -> cb.invokestatic(CD_Float, "floatToRawIntBits", MethodTypeDesc.of(CD_int, CD_float));
						case _64 ->
							cb.invokestatic(CD_Double, "doubleToRawLongBits", MethodTypeDesc.of(CD_long, CD_double));
					}
					stackTypes.set(stackTypes.size() - 1, intSizeDesc(Objects.requireNonNull(numSize)));
				}
			}
		}

		private void generateParametricInstr(ParametricInstr instr) {
			switch(instr) {
				case ParametricInstr.Drop() -> {
					var t = stackTypes.getLast();
					stackTypes.removeLast();

					if(slotSize(t) == 2) {
						cb.pop2();
					}
					else {
						cb.pop();
					}
				}
				case ParametricInstr.Select(var selectedTypes) -> {
					stackTypes.removeLast();
					var t = stackTypes.getLast();
					stackTypes.removeLast();

					var endLabel = cb.newLabel();
					var bottomLabel = cb.newLabel();

					cb.ifeq(bottomLabel);

					if(slotSize(t) == 2) {
						cb.pop2();
					}
					else {
						cb.pop();
					}

					cb.goto_(endLabel);

					cb.labelBinding(bottomLabel);

					if(slotSize(t) == 2) {
						cb.dup2_x2();
						cb.pop2();
						cb.pop2();
					}
					else {
						cb.swap();
						cb.pop();
					}

					cb.labelBinding(endLabel);
					if(selectedTypes != null) {
						var selectedType = stackType(
							compiler.getValType(closure.resolveValType(selectedTypes.getFirst())).type()
						);
						stackTypes.set(stackTypes.size() - 1, selectedType);
						if(!selectedType.isPrimitive()) {
							cb.checkcast(selectedType);
						}
					}
				}
			}
		}

		private void generateReferenceInstr(ReferenceInstr instr) {
			switch(instr) {
				case ReferenceInstr.Ref_Func(var funcIdx) -> {
					var funcInfo = funcs.get(funcIdx.index());
					if(!(compiler.getDefType(funcInfo.defType) instanceof FuncTypeRealization realizedFuncType)) {
						throw new RuntimeException("Function elementType realization");
					}

					var realizedMethodType = realizedFuncType.methodType().get();

					var callSite = DynamicCallSiteDesc.of(
						MethodHandleDesc.ofMethod(
							DirectMethodHandleDesc.Kind.STATIC,
							ClassDesc.of("java.lang.invoke.LambdaMetafactory"),
							"metafactory",
							MethodTypeDesc.of(
								ClassDesc.of("java.lang.invoke.CallSite"),
								ClassDesc.of("java.lang.invoke.MethodHandles$Lookup"),
								CD_String,
								ClassDesc.of("java.lang.invoke.MethodType"),
								ClassDesc.of("java.lang.invoke.MethodType"),
								ClassDesc.of("java.lang.invoke.MethodHandle"),
								ClassDesc.of("java.lang.invoke.MethodType")
							)
						),
						realizedFuncType.methodName(),
						MethodTypeDesc.of(realizedFuncType.classDesc(), className),

						realizedMethodType.descriptor(),
						MethodHandleDesc.ofMethod(
							DirectMethodHandleDesc.Kind.STATIC,
							funcInfo.methodsClassName(),
							funcInfo.name(),
							funcInfo.lambdaMethodType()
						),
						realizedMethodType.descriptor()
					);

					loadModuleInstance();
					cb.invokedynamic(callSite);
					stackTypes.add(realizedFuncType.classDesc());
				}

				case ReferenceInstr.Ref_IsNull() -> {
					cb.invokestatic(
						ClassDesc.of("java.util.Objects"),
						"isNull",
						MethodTypeDesc.of(CD_boolean, CD_Object)
					);
					stackTypes.removeLast();
					stackTypes.add(CD_int);
				}

				case ReferenceInstr.Ref_Null(var heapType) -> {
					cb.aconst_null();
					stackTypes.add(compiler.getValType(closure.resolveRefType(new RefType(true, heapType))).type());
				}

				case ReferenceInstr.Ref_AsNonNull() -> {
					cb.dup();
					cb.invokestatic(
						ClassDesc.of("java.util.Objects"),
						"requireNonNull",
						MethodTypeDesc.of(CD_Object, CD_Object)
					);
					cb.pop();
				}

				case ReferenceInstr.Ref_Cast(var refType) -> {
					var refType2 = closure.resolveRefType(refType);
					var realized = compiler.getValType(refType2);

					var classCast = ClassDesc.of("java.lang.ClassCastException");

					if(isBottomType(refType2.heapType())) {
						if(realized.isNullable()) {
							var nullLabel = cb.newLabel();

							cb.dup();
							cb.ifnull(nullLabel);
							cb.new_(classCast);
							cb.dup();
							cb.invokespecial(classCast, "<init>", MethodTypeDesc.of(CD_void));
							cb.athrow();
							cb.labelBinding(nullLabel);

							// Do this so the verifier knows the value is null.
							cb.pop();
							cb.aconst_null();
						}
						else {
							// Empty type so always throw.
							cb.new_(classCast);
							cb.dup();
							cb.invokespecial(classCast, "<init>", MethodTypeDesc.of(CD_void));
							isUnreachable = true;
							stackTypes.clear();
						}
					}
					else {
						if(!realized.isNullable()) {
							var notNullLabel = cb.newLabel();

							cb.dup();
							cb.ifnonnull(notNullLabel);
							cb.new_(classCast);
							cb.dup();
							cb.invokespecial(classCast, "<init>", MethodTypeDesc.of(CD_void));
							cb.athrow();
							cb.labelBinding(notNullLabel);
						}

						cb.checkcast(realized.type());
					}
					if(!isUnreachable) {
						stackTypes.set(stackTypes.size() - 1, realized.type());
					}
				}
				case ReferenceInstr.Ref_Eq() -> {
					cb.invokestatic(wasmEq, "isEqual", MethodTypeDesc.of(CD_boolean, wasmEq, wasmEq), true);
					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.add(CD_int);
				}
				case ReferenceInstr.Ref_I31() -> {
					storeScratch(TypeKind.INT, tempVarSlot);
					cb.new_(i31Type);
					cb.dup();
					loadScratch(TypeKind.INT, tempVarSlot);
					cb.invokespecial(i31Type, "<init>", MethodTypeDesc.of(CD_void, CD_int));
					stackTypes.removeLast();
					stackTypes.add(i31Type);
				}
				case ReferenceInstr.I31_Get_S() -> {
					cb.invokevirtual(i31Type, "signedValue", MethodTypeDesc.of(CD_int));
					stackTypes.removeLast();
					stackTypes.add(CD_int);
				}
				case ReferenceInstr.I31_Get_U() -> {
					cb.invokevirtual(i31Type, "unsignedValue", MethodTypeDesc.of(CD_int));
					stackTypes.removeLast();
					stackTypes.add(CD_int);
				}
				case ReferenceInstr.Ref_Test(var t) -> {
					var t2 = closure.resolveRefType(t);
					var realization = compiler.getValType(t2);

					if(isBottomType(t2.heapType())) {
						if(t.isNullable()) {
							cb.invokestatic(
								ClassDesc.of("java.util.Objects"),
								"isNull",
								MethodTypeDesc.of(CD_boolean, CD_Object)
							);
						}
						else {
							// Empty type so always false.
							cb.pop();
							cb.iconst_0();
						}
					}
					else {
						if(t.isNullable()) {
							var notNullLabel = cb.newLabel();
							var resultLabel = cb.newLabel();

							cb.dup();
							cb.ifnonnull(notNullLabel);
							cb.pop();
							cb.iconst_1();
							cb.goto_(resultLabel);
							cb.labelBinding(notNullLabel);
							cb.instanceOf(realization.type());
							cb.labelBinding(resultLabel);
						}
						else {
							cb.instanceOf(realization.type());
						}
					}

					stackTypes.removeLast();
					stackTypes.add(CD_int);
				}
				case ReferenceInstr.Any_Convert_Extern(),ReferenceInstr.Extern_Convert_Any() -> {
				}

				case ReferenceInstr.Struct_New(var typeIdx) -> {
					var defType = types.get(typeIdx.index());
					var realization = (StructTypeRealization)compiler.getDefType(defType);

					var createMethodType = realization.createMethodType().get();

					cb.invokestatic(
						realization.classDesc(),
						realization.createMethodName(),
						createMethodType,
						realization.isInterface()
					);

					for(var _ : createMethodType.parameterList()) {
						stackTypes.removeLast();
					}

					stackTypes.add(realization.classDesc());
				}
				case ReferenceInstr.Struct_New_Default(var typeIdx) -> {
					var defType = types.get(typeIdx.index());
					var structType = getStructType(defType);
					var realization = (StructTypeRealization)compiler.getDefType(defType);

					var createMethodType = realization.createMethodType().get();

					for(var t : structType.fields()) {
						pushDefaultValue(t.storageType());
					}

					cb.invokestatic(
						realization.classDesc(),
						realization.createMethodName(),
						createMethodType,
						realization.isInterface()
					);

					stackTypes.add(realization.classDesc());
				}
				case ReferenceInstr.Struct_Get(var typeIdx, var fieldIdx) -> {
					var defType = types.get(typeIdx.index());
					var realization = (StructTypeRealization)compiler.getDefType(defType);

					var fieldRealization = realization.fields().get(fieldIdx.index());
					var getMethod = fieldRealization.getMethod();

					cb.invokevirtual(realization.classDesc(), getMethod.methodName(), getMethod.methodType().get());

					stackTypes.removeLast();
					stackTypes.add(stackType(fieldRealization.fieldType().get()));
				}
				case ReferenceInstr.Struct_Get_S(var typeIdx, var fieldIdx) -> {
					var defType = types.get(typeIdx.index());
					var realization = (StructTypeRealization)compiler.getDefType(defType);

					var fieldRealization = realization.fields().get(fieldIdx.index());
					var getMethod = fieldRealization.getMethod();

					cb.invokevirtual(realization.classDesc(), getMethod.methodName(), getMethod.methodType().get());

					stackTypes.removeLast();
					stackTypes.add(stackType(fieldRealization.fieldType().get()));
				}
				case ReferenceInstr.Struct_Get_U(var typeIdx, var fieldIdx) -> {
					var defType = types.get(typeIdx.index());
					var realization = (StructTypeRealization)compiler.getDefType(defType);

					var fieldRealization = realization.fields().get(fieldIdx.index());
					var fieldClass = fieldRealization.fieldType().get();
					var getMethod = fieldRealization.getMethod();

					cb.invokevirtual(realization.classDesc(), getMethod.methodName(), getMethod.methodType().get());
					if(fieldClass.equals(CD_byte)) {
						cb.invokestatic(CD_Byte, "toUnsignedInt", MethodTypeDesc.ofDescriptor("(B)I"));
					}
					else if(fieldClass.equals(CD_short)) {
						cb.invokestatic(CD_Short, "toUnsignedInt", MethodTypeDesc.ofDescriptor("(S)I"));
					}
					else {
						throw new RuntimeException("Unexpected type for Array_Get_U");
					}

					stackTypes.removeLast();
					stackTypes.add(stackType(fieldRealization.fieldType().get()));
				}
				case ReferenceInstr.Struct_Set(var typeIdx, var fieldIdx) -> {
					var defType = types.get(typeIdx.index());
					var realization = (StructTypeRealization)compiler.getDefType(defType);

					var fieldRealization = realization.fields().get(fieldIdx.index());
					var setMethod = fieldRealization.setMethod();
					Objects.requireNonNull(setMethod);

					cb.invokevirtual(realization.classDesc(), setMethod.methodName(), setMethod.methodType().get());

					stackTypes.removeLast();
					stackTypes.removeLast();
				}

				case ReferenceInstr.Array_New(var typeIdx) -> {
					var defType = types.get(typeIdx.index());
					var arrayType = getArrayType(defType);
					var realization = (ArrayTypeRealization)compiler.getDefType(defType);
					var elementType = arrayType.fieldType().storageType();
					var elementClass = compiler.getStorageType(elementType);

					cb.invokestatic(
						realization.classDesc(),
						"nCopies",
						MethodTypeDesc.of(realization.classDesc(), elementClass.type(), CD_int)
					);

					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.add(realization.classDesc());
				}
				case ReferenceInstr.Array_New_Default(var typeIdx) -> {
					var defType = types.get(typeIdx.index());
					var realization = (ArrayTypeRealization)compiler.getDefType(defType);

					cb.invokestatic(
						realization.classDesc(),
						"ofDefault",
						MethodTypeDesc.of(realization.classDesc(), CD_int)
					);

					stackTypes.removeLast();
					stackTypes.add(realization.classDesc());
				}
				case ReferenceInstr.Array_New_Fixed(var typeIdx, int n) -> {
					var defType = types.get(typeIdx.index());
					var arrayType = getArrayType(defType);
					var realization = (ArrayTypeRealization)compiler.getDefType(defType);
					var elementType = arrayType.fieldType().storageType();
					var elementClass = compiler.getStorageType(elementType);

					if(n == 0) {
						cb.invokestatic(realization.classDesc(), "empty", MethodTypeDesc.of(realization.classDesc()));
					}
					else {
						cb.loadConstant(n);
						if(elementClass.type().isPrimitive()) {
							cb.newarray(typeKind(elementClass.type()));
						}
						else {
							cb.anewarray(elementClass.type());
						}
						storeScratch(TypeKind.REFERENCE, tempVarSlot);

						for(int i = n - 1; i >= 0; --i) {
							storeScratch(typeKind(elementClass.type()), tempVarSlot + 1);
							loadScratch(TypeKind.REFERENCE, tempVarSlot);
							cb.loadConstant(i);
							loadScratch(typeKind(elementClass.type()), tempVarSlot + 1);
							cb.arrayStore(typeKind(elementClass.type()));
						}

						loadScratch(TypeKind.REFERENCE, tempVarSlot);
						cb.invokestatic(
							realization.classDesc(),
							"copyOfJavaArray",
							MethodTypeDesc.of(realization.classDesc(), elementClass.type().arrayType())
						);
					}

					for(int i = 0; i < n; ++i) {
						stackTypes.removeLast();
					}
					stackTypes.add(realization.classDesc());
				}
				case ReferenceInstr.Array_New_Data(var typeIdx, var dataIdx) -> {
					var defType = types.get(typeIdx.index());
					var realization = (ArrayTypeRealization)compiler.getDefType(defType);

					loadModuleInstance();
					cb.getfield(className, "data" + dataIdx.index(), CD_byte.arrayType());

					cb.invokestatic(
						realization.classDesc(),
						"copyOfData",
						MethodTypeDesc.of(realization.classDesc(), CD_int, CD_int, CD_byte.arrayType())
					);

					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.add(realization.classDesc());
				}
				case ReferenceInstr.Array_New_Elem(var typeIdx, var elemIdx) -> {
					var defType = types.get(typeIdx.index());
					var realization = (ArrayTypeRealization)compiler.getDefType(defType);
					var elemInfo = elems.get(elemIdx.index());

					loadModuleInstance();
					cb.getfield(className, elemInfo.fieldName, elemInfo.fieldType);
					cb.invokestatic(
						realization.classDesc(),
						"copyOfJavaArray",
						MethodTypeDesc
							.of(realization.classDesc(), CD_int, CD_int, realization.elementType().get().arrayType())
					);

					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.add(realization.classDesc());
				}
				case ReferenceInstr.Array_Get(var typeIdx) -> {
					var defType = types.get(typeIdx.index());
					var realization = (ArrayTypeRealization)compiler.getDefType(defType);
					var elementClass = realization.elementType().get();

					cb.invokevirtual(realization.classDesc(), "get", MethodTypeDesc.of(elementClass, CD_int));

					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.add(stackType(elementClass));
				}
				case ReferenceInstr.Array_Get_S(var typeIdx) -> {
					var defType = types.get(typeIdx.index());
					var realization = (ArrayTypeRealization)compiler.getDefType(defType);
					var elementClass = realization.elementType().get();

					cb.invokevirtual(realization.classDesc(), "get", MethodTypeDesc.of(elementClass, CD_int));

					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.add(CD_int);
				}
				case ReferenceInstr.Array_Get_U(var typeIdx) -> {
					var defType = types.get(typeIdx.index());
					var realization = (ArrayTypeRealization)compiler.getDefType(defType);
					var elementClass = realization.elementType().get();

					cb.invokevirtual(realization.classDesc(), "get", MethodTypeDesc.of(elementClass, CD_int));
					if(elementClass.equals(CD_byte)) {
						cb.invokestatic(CD_Byte, "toUnsignedInt", MethodTypeDesc.ofDescriptor("(B)I"));
					}
					else if(elementClass.equals(CD_short)) {
						cb.invokestatic(CD_Short, "toUnsignedInt", MethodTypeDesc.ofDescriptor("(S)I"));
					}
					else {
						throw new RuntimeException("Unexpected type for Array_Get_U");
					}

					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.add(CD_int);
				}
				case ReferenceInstr.Array_Set(var typeIdx) -> {
					var defType = types.get(typeIdx.index());
					var realization = (ArrayTypeRealization)compiler.getDefType(defType);
					var elementClass = realization.elementType().get();

					cb.invokevirtual(realization.classDesc(), "set", MethodTypeDesc.of(CD_void, CD_int, elementClass));

					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.removeLast();

				}
				case ReferenceInstr.Array_Len() -> {
					cb.invokevirtual(wasmArray, "length", MethodTypeDesc.of(CD_int));
					stackTypes.removeLast();
					stackTypes.add(CD_int);
				}
				case ReferenceInstr.Array_Fill(var typeIdx) -> {
					var defType = types.get(typeIdx.index());
					var realization = (ArrayTypeRealization)compiler.getDefType(defType);
					var elementClass = realization.elementType().get();

					cb.invokevirtual(
						realization.classDesc(),
						"fill",
						MethodTypeDesc.of(CD_void, CD_int, elementClass, CD_int)
					);

					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.removeLast();
				}
				case ReferenceInstr.Array_Copy _ -> {
					cb.invokestatic(
						wasmArray,
						"copy",
						MethodTypeDesc.of(CD_void, wasmArrayMutable, CD_int, wasmArray, CD_int, CD_int)
					);
					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.removeLast();
				}
				case ReferenceInstr.Array_Init_Data(var typeIdx, var dataIdx) -> {
					var defType = types.get(typeIdx.index());
					var realization = (ArrayTypeRealization)compiler.getDefType(defType);

					loadModuleInstance();
					cb.getfield(className, "data" + dataIdx.index(), CD_byte.arrayType());
					cb.invokevirtual(
						realization.classDesc(),
						"copyFromData",
						MethodTypeDesc.of(CD_void, CD_int, CD_int, CD_int, CD_byte.arrayType())
					);

					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.removeLast();
				}
				case ReferenceInstr.Array_Init_Elem(var _, var elemIdx) -> {
					var elemInfo = elems.get(elemIdx.index());

					loadModuleInstance();
					cb.getfield(className, elemInfo.fieldName, elemInfo.fieldType);
					cb.invokestatic(
						wasmArray,
						"copyFromArray",
						MethodTypeDesc.of(CD_void, wasmArrayMutable, CD_int, CD_int, CD_int, CD_Object.arrayType())
					);

					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.removeLast();
				}
			}
		}

		private void pushDefaultValue(StorageType elementType) {
			switch(elementType) {
				case PackedType.I8, PackedType.I16, NumType.I32 -> cb.iconst_0();
				case NumType.I64 -> cb.lconst_0();
				case NumType.F32 -> cb.fconst_0();
				case NumType.F64 -> cb.dconst_0();
				case VecType.V128 -> loadV128(cb, V128.ZERO);
				case RefType _ -> cb.aconst_null();
				case BotType _ -> throw new RuntimeException("Unexpected bot type");
			}
		}

		private void generateTableInstr(TableInstr instr) {
			switch(instr) {
				case TableInstr.Table_Get(var tableIdx) -> {
					var tableInfo = tables.get(tableIdx.index());
					loadModuleInstance();
					cb.getfield(className, tableInfo.fieldName, wasmTable);
					cb.invokestatic(
						wasmTable,
						"table_get",
						MethodTypeDesc.of(CD_Object, addrDesc(tableInfo.tableType.addrType()), wasmTable)
					);
					if(!tableInfo.elementType.equals(CD_Object)) {
						cb.checkcast(tableInfo.elementType);
					}
					stackTypes.removeLast();
					stackTypes.add(tableInfo.elementType);
				}
				case TableInstr.Table_Set(var tableIdx) -> {
					var tableInfo = tables.get(tableIdx.index());
					loadModuleInstance();
					cb.getfield(className, tableInfo.fieldName, wasmTable);
					cb.invokestatic(
						wasmTable,
						"table_set",
						MethodTypeDesc.of(CD_void, addrDesc(tableInfo.tableType.addrType()), CD_Object, wasmTable)
					);
					stackTypes.removeLast();
					stackTypes.removeLast();
				}
				case TableInstr.Table_Size(var tableIdx) -> {
					var tableInfo = tables.get(tableIdx.index());
					loadModuleInstance();
					cb.getfield(className, tableInfo.fieldName, wasmTable);
					cb.invokevirtual(wasmTable, "size", MethodTypeDesc.of(CD_int));
					switch(tableInfo.tableType.addrType()) {
						case I32 -> {
						}
						case I64 -> cb.i2l();
					}
					stackTypes.add(stackType(addrDesc(tableInfo.tableType.addrType())));
				}
				case TableInstr.Table_Grow(var tableIdx) -> {
					var tableInfo = tables.get(tableIdx.index());
					var at = addrDesc(tableInfo.tableType.addrType());
					loadModuleInstance();
					cb.getfield(className, tableInfo.fieldName, wasmTable);
					cb.invokestatic(wasmTable, "table_grow", MethodTypeDesc.of(at, CD_Object, at, wasmTable));
					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.add(stackType(at));
				}
				case TableInstr.Table_Fill(var tableIdx) -> {
					var tableInfo = tables.get(tableIdx.index());
					var at = addrDesc(tableInfo.tableType.addrType());

					loadModuleInstance();
					cb.getfield(className, tableInfo.fieldName, wasmTable);
					cb.invokestatic(wasmTable, "table_fill", MethodTypeDesc.of(CD_void, at, CD_Object, at, wasmTable));

					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.removeLast();
				}
				case TableInstr.Table_Copy(var destTableIdx, var srcTableIdx) -> {
					var destTable = tables.get(destTableIdx.index());
					var srcTable = tables.get(srcTableIdx.index());

					var dat = addrDesc(destTable.tableType.addrType());
					var sat = addrDesc(srcTable.tableType.addrType());
					var minAt = addrDesc(switch(destTable.tableType.addrType()) {
						case I32 -> AddrType.I32;
						case I64 -> srcTable.tableType.addrType();
					});

					loadModuleInstance();
					cb.getfield(className, destTable.fieldName, wasmTable);

					loadModuleInstance();
					cb.getfield(className, srcTable.fieldName, wasmTable);

					cb.invokestatic(
						wasmTable,
						"copy",
						MethodTypeDesc.of(CD_void, dat, sat, minAt, wasmTable, wasmTable)
					);
					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.removeLast();
				}
				case TableInstr.Table_Init(var tableIdx, var elemIdx) -> {
					var table = tables.get(tableIdx.index());
					var elem = elems.get(elemIdx.index());

					var at = addrDesc(table.tableType.addrType());

					loadModuleInstance();
					cb.getfield(className, elem.fieldName, elem.fieldType);

					loadModuleInstance();
					cb.getfield(className, table.fieldName, wasmTable);

					cb.invokestatic(
						wasmTable,
						"copyFromArray",
						MethodTypeDesc.of(CD_void, at, CD_int, CD_int, CD_Object.arrayType(), wasmTable)
					);
					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.removeLast();
				}
				case TableInstr.Elem_Drop(var elemIdx) -> {
					var elem = elems.get(elemIdx.index());
					loadModuleInstance();
					cb.iconst_0();
					cb.anewarray(elem.elementType);
					cb.putfield(className, elem.fieldName, elem.fieldType);
				}
			}
		}

		private void generateVariableInstr(VariableInstr instr) {
			switch(instr) {
				case VariableInstr.Local_Get(var localIdx) -> {
					var localType = localType(localIdx.index());
					loadLocal(localIdx.index());
					stackTypes.add(localType);
				}
				case VariableInstr.Local_Set(var localIdx) -> {
					storeLocal(localIdx.index());
					stackTypes.removeLast();
				}
				case VariableInstr.Local_Tee(var localIdx) -> {
					var localType = localType(localIdx.index());
					if(typeKind(localType).slotSize() == 2) {
						cb.dup2();
					}
					else {
						cb.dup();
					}
					storeLocal(localIdx.index());
				}
				case VariableInstr.Global_Get(var globalIdx) -> {
					var globalInfo = globals.get(globalIdx.index());

					loadModuleInstance();
					cb.getfield(className, globalInfo.fieldName, globalInfo.containerType);

					if(globalInfo.globalType.mutability() == Mut.Var) {
						if(globalInfo.globalType.type() instanceof NumType numType) {
							var getDesc = switch(numType) {
								case I32 -> MethodTypeDesc.of(CD_int);
								case I64 -> MethodTypeDesc.of(CD_long);
								case F32 -> MethodTypeDesc.of(CD_float);
								case F64 -> MethodTypeDesc.of(CD_double);
							};

							cb.invokevirtual(globalInfo.containerType, "get", getDesc);
						}
						else {
							cb.invokevirtual(globalInfo.containerType, "get", MethodTypeDesc.of(CD_Object));
							if(!globalInfo.elementType.equals(CD_Object)) {
								cb.checkcast(globalInfo.elementType);
							}
						}
					}

					stackTypes.add(stackType(globalInfo.elementType));
				}
				case VariableInstr.Global_Set(var globalIdx) -> {
					var globalInfo = globals.get(globalIdx.index());

					loadModuleInstance();
					cb.getfield(className, globalInfo.fieldName, globalInfo.containerType);

					var desc = switch(globalInfo.globalType.type()) {
						case NumType numType -> switch(numType) {
							case I32 -> MethodTypeDesc.of(CD_void, CD_int);
							case I64 -> MethodTypeDesc.of(CD_void, CD_long);
							case F32 -> MethodTypeDesc.of(CD_void, CD_float);
							case F64 -> MethodTypeDesc.of(CD_void, CD_double);
						};
						default -> MethodTypeDesc.of(CD_void, CD_Object);
					};

					if(typeKind(globalInfo.elementType).slotSize() == 2) {
						cb.dup_x2();
						cb.pop();
					}
					else {
						cb.swap();
					}

					cb.invokevirtual(globalInfo.containerType, "set", desc);

					stackTypes.removeLast();
				}
			}
		}

		private void generateVectorInstr(VectorInstr instr) {
			switch(instr) {
				case VectorInstr.V128_Const(var value) -> {
					loadV128(cb, value);
					stackTypes.add(v128Type);
				}

				case VectorInstr.I8x16_Op_Instr(var op) -> {
					switch(op) {
						case VectorInstr.Swizzle() -> {
							cb.invokevirtual(v128Type, "swizzle8", MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.Shuffle(var laneIndexes) -> {
							loadV128(cb, laneIndexes);
							cb.invokevirtual(v128Type, "shuffle8", MethodTypeDesc.of(v128Type, v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.ExtractLane_U(var laneIdx) -> {
							cb.loadConstant(laneIdx);
							cb.invokevirtual(v128Type, "extractLane8", MethodTypeDesc.of(CD_byte, CD_int));
							cb.invokestatic(CD_Byte, "toUnsignedInt", MethodTypeDesc.ofDescriptor("(B)I"));
							stackTypes.removeLast();
							stackTypes.add(CD_int);
						}
						case VectorInstr.ExtractLane_S(var laneIdx) -> {
							cb.loadConstant(laneIdx);
							cb.invokevirtual(v128Type, "extractLane8", MethodTypeDesc.of(CD_byte, CD_int));
							stackTypes.removeLast();
							stackTypes.add(CD_int);
						}
						case VectorInstr.I8x16_Narrow_I16x8_S() -> {
							cb.invokevirtual(v128Type, "narrow16To8Signed", MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.I8x16_Narrow_I16x8_U() -> {
							cb.invokevirtual(v128Type, "narrow16To8Unsigned", MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.Popcnt() -> {
							cb.invokevirtual(v128Type, "popcnt8", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.VIAverageOps viAverageOps -> {
							switch(viAverageOps) {
								case AVGR_U -> {
									cb.invokevirtual(v128Type, "avgrU8", MethodTypeDesc.of(v128Type, v128Type));
									stackTypes.removeLast();
								}
							}
						}
						case VectorInstr.VIMinMaxOp viMinMaxOp -> {
							String opMethod = switch(viMinMaxOp) {
								case MIN_U -> "minU8";
								case MIN_S -> "minS8";
								case MAX_U -> "maxU8";
								case MAX_S -> "maxS8";
							};

							cb.invokevirtual(v128Type, opMethod, MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.VIOp viOp -> generateVectorIOp(viOp, "8", CD_byte);
						case VectorInstr.VIRelOp viRelOp -> generateVectorIRelOp(viRelOp, "8");
						case VectorInstr.VISatBinOp viSatBinOp -> {
							String opMethod = switch(viSatBinOp) {
								case ADD_SAT_U -> "addSatU8";
								case ADD_SAT_S -> "addSatS8";
								case SUB_SAT_U -> "subSatU8";
								case SUB_SAT_S -> "subSatS8";
							};

							cb.invokevirtual(v128Type, opMethod, MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
					};
				}
				case VectorInstr.I16x8_Op_Instr(var op) -> {
					switch(op) {
						case VectorInstr.ExtractLane_U(var laneIdx) -> {
							cb.loadConstant(laneIdx);
							cb.invokevirtual(v128Type, "extractLane16", MethodTypeDesc.of(CD_short, CD_int));
							cb.invokestatic(CD_Short, "toUnsignedInt", MethodTypeDesc.ofDescriptor("(S)I"));
							stackTypes.removeLast();
							stackTypes.add(CD_int);
						}
						case VectorInstr.ExtractLane_S(var laneIdx) -> {
							cb.loadConstant(laneIdx);
							cb.invokevirtual(v128Type, "extractLane16", MethodTypeDesc.of(CD_short, CD_int));
							stackTypes.removeLast();
							stackTypes.add(CD_int);
						}
						case VectorInstr.I16x8_Narrow_I32x4_S() -> {
							cb.invokevirtual(v128Type, "narrow32To16Signed", MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.I16x8_Narrow_I32x4_U() -> {
							cb.invokevirtual(v128Type, "narrow32To16Unsigned", MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.I16x8_Extend_Low_I8x16_S() -> {
							cb.invokevirtual(v128Type, "extendLowS8To16", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.I16x8_Extend_Low_I8x16_U() -> {
							cb.invokevirtual(v128Type, "extendLowU8To16", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.I16x8_Extend_High_I8x16_S() -> {
							cb.invokevirtual(v128Type, "extendHighS8To16", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.I16x8_Extend_High_I8x16_U() -> {
							cb.invokevirtual(v128Type, "extendHighU8To16", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.I16x8_ExtMul_Low_I8x16_S() -> {
							cb.invokevirtual(v128Type, "extmulLowS8", MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.I16x8_ExtMul_Low_I8x16_U() -> {
							cb.invokevirtual(v128Type, "extmulLowU8", MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.I16x8_ExtMul_High_I8x16_S() -> {
							cb.invokevirtual(v128Type, "extmulHighS8", MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.I16x8_ExtMul_High_I8x16_U() -> {
							cb.invokevirtual(v128Type, "extmulHighU8", MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.I16x8_ExtAdd_Pairwise_I8x16_S() -> {
							cb.invokevirtual(v128Type, "extaddPairwiseS8", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.I16x8_ExtAdd_Pairwise_I8x16_U() -> {
							cb.invokevirtual(v128Type, "extaddPairwiseU8", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.Q15mulr_Sat_S() -> {
							cb.invokevirtual(v128Type, "q15mulrSatS", MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.VIAverageOps viAverageOps -> {
							switch(viAverageOps) {
								case AVGR_U -> {
									cb.invokevirtual(v128Type, "avgrU16", MethodTypeDesc.of(v128Type, v128Type));
									stackTypes.removeLast();
								}
							}
						}
						case VectorInstr.VIMinMaxOp viMinMaxOp -> {
							String opMethod = switch(viMinMaxOp) {
								case MIN_U -> "minU16";
								case MIN_S -> "minS16";
								case MAX_U -> "maxU16";
								case MAX_S -> "maxS16";
							};

							cb.invokevirtual(v128Type, opMethod, MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.VIMulOp viMulOp -> {
							String opMethod = switch(viMulOp) {
								case MUL -> "mul16";
							};

							cb.invokevirtual(v128Type, opMethod, MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.VIOp viOp -> generateVectorIOp(viOp, "16", CD_short);
						case VectorInstr.VIRelOp viRelOp -> generateVectorIRelOp(viRelOp, "16");
						case VectorInstr.VISatBinOp viSatBinOp -> {
							String opMethod = switch(viSatBinOp) {
								case ADD_SAT_U -> "addSatU16";
								case ADD_SAT_S -> "addSatS16";
								case SUB_SAT_U -> "subSatU16";
								case SUB_SAT_S -> "subSatS16";
							};

							cb.invokevirtual(v128Type, opMethod, MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
					}
				}
				case VectorInstr.I32x4_Op_Instr(var op) -> {
					switch(op) {
						case VectorInstr.ExtractLane(var laneIdx) -> {
							cb.loadConstant(laneIdx);
							cb.invokevirtual(v128Type, "extractLane32", MethodTypeDesc.of(CD_int, CD_int));
							stackTypes.removeLast();
							stackTypes.add(CD_int);
						}
						case VectorInstr.Dot_I16x8_S() -> {
							cb.invokevirtual(v128Type, "dotS16x8", MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.I32x4_Extend_Low_I16x8_S() -> {
							cb.invokevirtual(v128Type, "extendLowS16To32", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.I32x4_Extend_Low_I16x8_U() -> {
							cb.invokevirtual(v128Type, "extendLowU16To32", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.I32x4_Extend_High_I16x8_S() -> {
							cb.invokevirtual(v128Type, "extendHighS16To32", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.I32x4_Extend_High_I16x8_U() -> {
							cb.invokevirtual(v128Type, "extendHighU16To32", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.I32x4_ExtMul_Low_I16x8_S() -> {
							cb.invokevirtual(v128Type, "extmulLowS16", MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.I32x4_ExtMul_Low_I16x8_U() -> {
							cb.invokevirtual(v128Type, "extmulLowU16", MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.I32x4_ExtMul_High_I16x8_S() -> {
							cb.invokevirtual(v128Type, "extmulHighS16", MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.I32x4_ExtMul_High_I16x8_U() -> {
							cb.invokevirtual(v128Type, "extmulHighU16", MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.I32x4_ExtAdd_Pairwise_I16x8_S() -> {
							cb.invokevirtual(v128Type, "extaddPairwiseS16", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.I32x4_ExtAdd_Pairwise_I16x8_U() -> {
							cb.invokevirtual(v128Type, "extaddPairwiseU16", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.I32x4_Trunc_Sat_F32x4_S() -> {
							cb.invokevirtual(v128Type, "truncateSatF32ToS32", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.I32x4_Trunc_Sat_F32x4_U() -> {
							cb.invokevirtual(v128Type, "truncateSatF32ToU32", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.I32x4_Trunc_Sat_F64x2_S_Zero() -> {
							cb.invokevirtual(v128Type, "truncateSatF64ToS32Zero", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.I32x4_Trunc_Sat_F64x4_U_Zero() -> {
							cb.invokevirtual(v128Type, "truncateSatF64ToU32Zero", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.VIMinMaxOp viMinMaxOp -> {
							String opMethod = switch(viMinMaxOp) {
								case MIN_U -> "minU32";
								case MIN_S -> "minS32";
								case MAX_U -> "maxU32";
								case MAX_S -> "maxS32";
							};

							cb.invokevirtual(v128Type, opMethod, MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.VIMulOp viMulOp -> {
							String opMethod = switch(viMulOp) {
								case MUL -> "mul32";
							};

							cb.invokevirtual(v128Type, opMethod, MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.VIOp viOp -> generateVectorIOp(viOp, "32", CD_int);
						case VectorInstr.VIRelOp viRelOp -> generateVectorIRelOp(viRelOp, "32");
					}
				}
				case VectorInstr.I64x2_Op_Instr(var op) -> {
					switch(op) {
						case VectorInstr.ExtractLane(var laneIdx) -> {
							cb.loadConstant(laneIdx);
							cb.invokevirtual(v128Type, "extractLane64", MethodTypeDesc.of(CD_long, CD_int));
							stackTypes.removeLast();
							stackTypes.add(CD_long);
						}
						case VectorInstr.I64x2_Extend_Low_I32x4_S() -> {
							cb.invokevirtual(v128Type, "extendLowS32To64", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.I64x2_Extend_Low_I32x4_U() -> {
							cb.invokevirtual(v128Type, "extendLowU32To64", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.I64x2_Extend_High_I32x4_S() -> {
							cb.invokevirtual(v128Type, "extendHighS32To64", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.I64x2_Extend_High_I32x4_U() -> {
							cb.invokevirtual(v128Type, "extendHighU32To64", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.I64x2_ExtMul_High_I32x4_S() -> {
							cb.invokevirtual(v128Type, "extmulLowS32", MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.I64x2_ExtMul_High_I32x4_U() -> {
							cb.invokevirtual(v128Type, "extmulLowU32", MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.I64x2_ExtMul_Low_I32x4_S() -> {
							cb.invokevirtual(v128Type, "extmulHighS32", MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.I64x2_ExtMul_Low_I32x4_U() -> {
							cb.invokevirtual(v128Type, "extmulHighU32", MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.VIMulOp viMulOp -> {
							String opMethod = switch(viMulOp) {
								case MUL -> "mul64";
							};

							cb.invokevirtual(v128Type, opMethod, MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.VIOp viOp -> generateVectorIOp(viOp, "64", CD_long);
						case VectorInstr.VIRelOp viRelOp -> generateVectorIRelOp(viRelOp, "64");
					}
				}
				case VectorInstr.F32x4_Op_Instr(var op) -> {
					switch(op) {
						case VectorInstr.F32x4_Convert_I32x4_S() -> {
							cb.invokevirtual(v128Type, "convertS32ToF32", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.F32x4_Convert_I32x4_U() -> {
							cb.invokevirtual(v128Type, "convertU32ToF32", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.F32x4_Demote_F64x2_Zero() -> {
							cb.invokevirtual(v128Type, "demoteF64ToF32Zero", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.VFOp vfOp -> generateVectorFOp(vfOp, "F32", CD_float);
					}
				}
				case VectorInstr.F32x4_Ternary_Op_Instr(var op) -> {
					switch(op) {
						case VectorInstr.Relaxed_F32x4_MAdd() -> {
							cb.invokevirtual(
								v128Type,
								"relaxedMaddF32",
								MethodTypeDesc.of(v128Type, v128Type, v128Type)
							);
							stackTypes.removeLast();
							stackTypes.removeLast();
						}
						case VectorInstr.Relaxed_F32x4_NMAdd() -> {
							cb.invokevirtual(
								v128Type,
								"relaxedNmaddF32",
								MethodTypeDesc.of(v128Type, v128Type, v128Type)
							);
							stackTypes.removeLast();
							stackTypes.removeLast();
						}
					}
				}
				case VectorInstr.F64x2_Op_Instr(var op) -> {
					switch(op) {
						case VectorInstr.F64x2_Convert_Low_I32x4_S() -> {
							cb.invokevirtual(v128Type, "convertS32LowToF64", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.F64x2_Convert_Low_I32x4_U() -> {
							cb.invokevirtual(v128Type, "convertU32LowToF64", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.F64x2_Promote_Low_F32x4() -> {
							cb.invokevirtual(v128Type, "promoteF32LowToF64", MethodTypeDesc.of(v128Type));
						}
						case VectorInstr.VFOp vfOp -> generateVectorFOp(vfOp, "F64", CD_double);
					}
				}
				case VectorInstr.F64x2_Ternary_Op_Instr(var op) -> {
					switch(op) {
						case VectorInstr.Relaxed_F64x2_MAdd() -> {
							cb.invokevirtual(
								v128Type,
								"relaxedMaddF64",
								MethodTypeDesc.of(v128Type, v128Type, v128Type)
							);
							stackTypes.removeLast();
							stackTypes.removeLast();
						}
						case VectorInstr.Relaxed_F64x2_NMAdd() -> {
							cb.invokevirtual(
								v128Type,
								"relaxedNmaddF64",
								MethodTypeDesc.of(v128Type, v128Type, v128Type)
							);
							stackTypes.removeLast();
							stackTypes.removeLast();
						}
					}
				}
				case VectorInstr.I16x8_Relaxed_Dot_I8x16_I7x16_S() -> {
					cb.invokevirtual(v128Type, "relaxedDotI8x16ByI7x16Signed", MethodTypeDesc.of(v128Type, v128Type));
					stackTypes.removeLast();
				}
				case VectorInstr.I32x4_Relaxed_Dot_I8x16_I7x16_Add_S() -> {
					cb.invokevirtual(
						v128Type,
						"relaxedDotI8x16ByI7x16AddSigned",
						MethodTypeDesc.of(v128Type, v128Type, v128Type)
					);
					stackTypes.removeLast();
					stackTypes.removeLast();
				}
				case VectorInstr.VVBinOp vvBinOp -> {
					String opMethod = switch(vvBinOp) {
						case AND -> "and";
						case ANDNOT -> "andNot";
						case OR -> "or";
						case XOR -> "xor";
					};

					cb.invokevirtual(v128Type, opMethod, MethodTypeDesc.of(v128Type, v128Type));
					stackTypes.removeLast();
				}
				case VectorInstr.VVTernOp vvTernOp -> {
					String opMethod = switch(vvTernOp) {
						case BITSELECT -> "bitselect";
					};

					cb.invokevirtual(v128Type, opMethod, MethodTypeDesc.of(v128Type, v128Type, v128Type));
					stackTypes.removeLast();
					stackTypes.removeLast();
				}
				case VectorInstr.VVTestOp vvTestOp -> {
					String opMethod = switch(vvTestOp) {
						case ANY_TRUE -> "anyTrue";
					};

					cb.invokevirtual(v128Type, opMethod, MethodTypeDesc.of(CD_boolean));
					stackTypes.removeLast();
					stackTypes.add(CD_int);
				}
				case VectorInstr.VVUnOp vvUnOp -> {
					String opMethod = switch(vvUnOp) {
						case NOT -> "bitNot";
					};

					cb.invokevirtual(v128Type, opMethod, MethodTypeDesc.of(v128Type));
				}
			}
		}

		private void generateVectorIOp(VectorInstr.VIOp op, String sizeSuffix, ClassDesc laneType) {
			switch(op) {
				case VectorInstr.ReplaceLane(var laneIdx) -> {
					cb.loadConstant(laneIdx);
					cb.invokevirtual(
						v128Type,
						"replaceLane" + sizeSuffix,
						MethodTypeDesc.of(v128Type, laneType, CD_int)
					);
					stackTypes.removeLast();
				}
				case VectorInstr.Splat() -> {
					cb.invokestatic(v128Type, "splat" + sizeSuffix, MethodTypeDesc.of(v128Type, laneType));
					stackTypes.removeLast();
					stackTypes.add(v128Type);
				}

				case VectorInstr.All_True() -> {
					cb.invokevirtual(v128Type, "allTrue" + sizeSuffix, MethodTypeDesc.of(CD_boolean));
					stackTypes.removeLast();
					stackTypes.add(CD_int);
				}
				case VectorInstr.BitMask() -> {
					cb.invokevirtual(v128Type, "bitmask" + sizeSuffix, MethodTypeDesc.of(CD_int));
				}
				case VectorInstr.VIBinOp viBinOp -> {
					String opPrefix = switch(viBinOp) {
						case ADD -> "add";
						case SUB -> "sub";
					};

					cb.invokevirtual(v128Type, opPrefix + sizeSuffix, MethodTypeDesc.of(v128Type, v128Type));
					stackTypes.removeLast();
				}
				case VectorInstr.VIShiftOp viShiftOp -> {
					String opPrefix = switch(viShiftOp) {
						case SHL -> "shl";
						case SHR_S -> "shrS";
						case SHR_U -> "shrU";
					};

					cb.invokevirtual(v128Type, opPrefix + sizeSuffix, MethodTypeDesc.of(v128Type, CD_int));
					stackTypes.removeLast();
				}
				case VectorInstr.VIUnOp viUnOp -> {
					String opPrefix = switch(viUnOp) {
						case ABS -> "abs";
						case NEG -> "neg";
					};

					cb.invokevirtual(v128Type, opPrefix + sizeSuffix, MethodTypeDesc.of(v128Type));
				}
			}
		}

		private void generateVectorIRelOp(VectorInstr.VIRelOp op, String sizeSuffix) {
			String opPrefix = switch(op) {
				case VectorInstr.VIRelOp_S viRelOpS -> switch(viRelOpS) {
					case EQ -> "equals";
					case NE -> "notEquals";
					case LT_S -> "lessThanSigned";
					case GT_S -> "greaterThanSigned";
					case LE_S -> "lessThanOrEqualSigned";
					case GE_S -> "greaterThanOrEqualSigned";
				};
				case VectorInstr.VIRelOp_U viRelOpU -> switch(viRelOpU) {
					case LT_U -> "lessThanUnsigned";
					case GT_U -> "greaterThanUnsigned";
					case LE_U -> "lessThanOrEqualUnsigned";
					case GE_U -> "greaterThanOrEqualUnsigned";
				};
			};

			cb.invokevirtual(v128Type, opPrefix + sizeSuffix, MethodTypeDesc.of(v128Type, v128Type));
			stackTypes.removeLast();
		}

		private void generateVectorFOp(VectorInstr.VFOp op, String sizeSuffix, ClassDesc laneType) {
			switch(op) {
				case VectorInstr.ExtractLane(var laneIdx) -> {
					cb.loadConstant(laneIdx);
					cb.invokevirtual(v128Type, "extractLane" + sizeSuffix, MethodTypeDesc.of(laneType, CD_int));
					stackTypes.removeLast();
					stackTypes.add(stackType(laneType));
				}
				case VectorInstr.ReplaceLane(var laneIdx) -> {
					cb.loadConstant(laneIdx);
					cb.invokevirtual(
						v128Type,
						"replaceLane" + sizeSuffix,
						MethodTypeDesc.of(v128Type, laneType, CD_int)
					);
					stackTypes.removeLast();
				}
				case VectorInstr.Splat() -> {
					cb.invokestatic(v128Type, "splat" + sizeSuffix, MethodTypeDesc.of(v128Type, laneType));
					stackTypes.removeLast();
					stackTypes.add(v128Type);
				}

				case VectorInstr.VFBinOp vfBinOp -> {
					String opPrefix = switch(vfBinOp) {
						case ADD -> "add";
						case SUB -> "sub";
						case MUL -> "mul";
						case DIV -> "div";
						case MIN -> "min";
						case MAX -> "max";
						case PMIN -> "pmin";
						case PMAX -> "pmax";
					};

					cb.invokevirtual(v128Type, opPrefix + sizeSuffix, MethodTypeDesc.of(v128Type, v128Type));
					stackTypes.removeLast();
				}
				case VectorInstr.VFRelOp vfRelOp -> {
					String opPrefix = switch(vfRelOp) {
						case EQ -> "equals";
						case NE -> "notEquals";
						case LT -> "lessThan";
						case GT -> "greaterThan";
						case LE -> "lessThanOrEqual";
						case GE -> "greaterThanOrEqual";
					};

					cb.invokevirtual(v128Type, opPrefix + sizeSuffix, MethodTypeDesc.of(v128Type, v128Type));
					stackTypes.removeLast();
				}
				case VectorInstr.VFUnOp vfUnOp -> {
					String opPrefix = switch(vfUnOp) {
						case ABS -> "abs";
						case NEG -> "neg";
						case SQRT -> "sqrt";
						case CEIL -> "ceil";
						case FLOOR -> "floor";
						case TRUNC -> "trunc";
						case NEAREST -> "nearest";
					};

					cb.invokevirtual(v128Type, opPrefix + sizeSuffix, MethodTypeDesc.of(v128Type));
				}
			}
		}

		private void loadMem(MemoryInstr.MemArg memArg) {
			loadMem(memArg.memIdx());
		}

		private void loadMem(MemIdx memIdx) {
			loadModuleInstance();
			var mem = mems.get(memIdx.index());
			cb.getfield(className, mem.fieldName, mem.memoryClass());
		}

		private void doLoad(ClassDesc t, MemoryInstr.MemArg memArg) {
			doLoadStore(true, t, memArg);
			stackTypes.removeLast();
			stackTypes.add(stackType(t));
		}

		private void doStore(ClassDesc t, MemoryInstr.MemArg memArg) {
			doLoadStore(false, t, memArg);
			stackTypes.removeLast();
			stackTypes.removeLast();
		}

		private void doLoadStore(boolean isLoad, ClassDesc t, MemoryInstr.MemArg memArg) {
			String prefix = isLoad ? "load" : "store";

			String suffix;
			if(t.equals(CD_byte)) {
				suffix = "I8";
			}
			else if(t.equals(CD_short)) {
				suffix = "I16";
			}
			else if(t.equals(CD_int)) {
				suffix = "I32";
			}
			else if(t.equals(CD_long)) {
				suffix = "I64";
			}
			else if(t.equals(CD_float)) {
				suffix = "F32";
			}
			else if(t.equals(CD_double)) {
				suffix = "F64";
			}
			else if(t.equals(ClassDesc.of(RUNTIME_PACKAGE, "V128"))) {
				suffix = "V128";
			}
			else {
				throw new RuntimeException("Unexpected elementType for memory load");
			}

			var mem = mems.get(memArg.memIdx().index());
			var wasmMemory = mem.memoryClass();
			var at = addrDesc(mem.memType.addrType());

			if(memArg.offset() == 0) {
				loadMem(memArg);
				cb.invokestatic(
					wasmMemory,
					prefix + suffix,
					isLoad ? MethodTypeDesc.of(t, at, wasmMemory) : MethodTypeDesc.of(CD_void, at, t, wasmMemory)
				);
				return;
			}

			switch(mem.memType.addrType()) {
				case I32 -> cb.loadConstant((int)memArg.offset());
				case I64 -> cb.loadConstant(memArg.offset());
			}

			loadMem(memArg);

			cb.invokestatic(
				wasmMemory,
				prefix + suffix,
				isLoad ? MethodTypeDesc.of(t, at, at, wasmMemory) : MethodTypeDesc.of(CD_void, at, t, at, wasmMemory)
			);
		}

		protected final boolean isBottomType(HeapType t) {
			return switch(t) {
				case HeapType.AbstractHeapType.NONE, HeapType.AbstractHeapType.NOFUNC, HeapType.AbstractHeapType.NOEXN,
					HeapType.AbstractHeapType.NOEXTERN -> true;
				default -> false;
			};
		}

		protected final void generateNop() {
			cb.nop();
		}

		protected final void generateUnreachable() {
			var trap = ClassDesc.of(RUNTIME_PACKAGE, "UnreachableTrap");
			cb.new_(trap);
			cb.dup();
			cb.invokespecial(trap, "<init>", MethodTypeDesc.ofDescriptor("()V"));
			cb.athrow();
			isUnreachable = true;
			stackTypes.clear();
		}

		protected final void generateThrow(ControlInstr.Throw instr) {
			var tagIdx = instr.tag();
			var tagInfo = tags.get(tagIdx.index());

			saveStackTempRes(tagInfo.funcType.args());
			cb.new_(tagInfo.realization.classDesc());
			cb.dup();
			restoreStackTempRes(tagInfo.funcType.args());
			cb.invokespecial(tagInfo.realization.classDesc(), "<init>", tagInfo.realization.constructorType());
			cb.athrow();

			stackTypes.clear();
			isUnreachable = true;
		}

		protected final void generateThrowRef() {
			cb.athrow();
			stackTypes.clear();
			isUnreachable = true;
		}

		protected final void generateCall(ControlInstr.Call instr) {
			var funcIdx = instr.func();
			var funcInfo = funcs.get(funcIdx.index());

			var resultType = compiler.getResultType(funcInfo.funcType.results()).classDesc();
			var endResultType = resultType.nested("EndResult");

			for(int i = 0; i < funcInfo.methodType.descriptor().parameterCount(); ++i) {
				stackTypes.removeLast();
			}

			loadModuleInstance();
			cb.invokestatic(funcInfo.methodsClassName(), funcInfo.name(), funcInfo.invokeMethodType());
			cb.invokestatic(resultType, "get", MethodTypeDesc.of(endResultType, resultType), true);
			unpackResultType(funcInfo.funcType.results(), endResultType);
		}

		protected final void generateCallRef(ControlInstr.Call_Ref instr) {
			var funcTypeIdx = instr.funcType();
			var defType = closure.resolveDefType(types.get(funcTypeIdx.index()));
			var realizedType = (FuncTypeRealization)compiler.getDefType(defType);
			var realizedMethodType = realizedType.methodType().get();
			var funcType = getFuncType(defType);

			var functionObjSlot = tempVarSlot;
			++tempVarSlot;

			storeScratch(TypeKind.REFERENCE, functionObjSlot);
			stackTypes.removeLast();

			saveStackTempDesc(realizedMethodType.descriptor().parameterList());

			loadScratch(TypeKind.REFERENCE, functionObjSlot);
			stackTypes.add(realizedType.classDesc());

			restoreStackTempDesc(realizedMethodType.descriptor().parameterList());

			for(var _ : realizedMethodType.descriptor().parameterList()) {
				stackTypes.removeLast();
			}

			stackTypes.removeLast();

			var resultType = realizedMethodType.descriptor().returnType();
			var endResultType = resultType.nested("EndResult");

			cb.invokeinterface(
				realizedType.classDesc(),
				realizedType.methodName(),
				realizedMethodType.descriptor()
			);
			cb.invokestatic(resultType, "get", MethodTypeDesc.of(endResultType, resultType), true);
			unpackResultType(funcType.results(), endResultType);

			tempVarSlot = functionObjSlot;
		}

		protected final void generateCallIndirect(ControlInstr.Call_Indirect instr) {
			var tableIdx = instr.table();
			var funcTypeIdx = instr.funcType();
			var tableInfo = tables.get(tableIdx.index());
			loadModuleInstance();
			cb.getfield(className, tableInfo.fieldName, wasmTable);
			cb.invokestatic(
				wasmTable,
				"table_get",
				MethodTypeDesc.of(CD_Object, addrDesc(tableInfo.tableType.addrType()), wasmTable)
			);

			var defType = types.get(funcTypeIdx.index());
			var realizedType = compiler.getDefType(defType);
			cb.checkcast(realizedType.classDesc());

			stackTypes.removeLast();
			stackTypes.add(realizedType.classDesc());

			generateCallRef(new ControlInstr.Call_Ref(funcTypeIdx));
		}

		protected final void generateReturnCall(ControlInstr.Return_Call instr) {
			var funcIdx = instr.func();
			var funcInfo = funcs.get(funcIdx.index());

			var resultType = compiler.getResultType(funcInfo.funcType.results()).classDesc();
			var stepResultType = resultType.nested("Step");

			var invokeDynamicSig = funcInfo.invokeMethodType().changeReturnType(stepResultType);

			var stepSig = MethodTypeDesc.of(resultType);

			var callSite = DynamicCallSiteDesc.of(
				MethodHandleDesc.ofMethod(
					DirectMethodHandleDesc.Kind.STATIC,
					ClassDesc.of("java.lang.invoke.LambdaMetafactory"),
					"metafactory",
					MethodTypeDesc.of(
						ClassDesc.of("java.lang.invoke.CallSite"),
						ClassDesc.of("java.lang.invoke.MethodHandles$Lookup"),
						CD_String,
						ClassDesc.of("java.lang.invoke.MethodType"),
						ClassDesc.of("java.lang.invoke.MethodType"),
						ClassDesc.of("java.lang.invoke.MethodHandle"),
						ClassDesc.of("java.lang.invoke.MethodType")
					)
				),
				"step",
				invokeDynamicSig,

				stepSig,
				MethodHandleDesc.ofMethod(
					DirectMethodHandleDesc.Kind.STATIC,
					funcInfo.methodsClassName(),
					funcInfo.name(),
					funcInfo.invokeMethodType()
				),
				stepSig
			);

			loadModuleInstance();
			cb.invokedynamic(callSite);
			emitResultReturn();
			stackTypes.clear();
			isUnreachable = true;
		}

		protected final void generateReturnCallRef(ControlInstr.Return_Call_Ref instr) {
			var funcTypeIdx = instr.funcType();
			var defType = closure.resolveDefType(types.get(funcTypeIdx.index()));
			var realizedType = (FuncTypeRealization)compiler.getDefType(defType);
			var realizedMethodType = realizedType.methodType().get();

			cb.dup();
			cb.invokestatic(
				ClassDesc.of("java.util.Objects"),
				"requireNonNull",
				MethodTypeDesc.of(CD_Object, CD_Object)
			);
			cb.pop();

			var functionObjSlot = tempVarSlot;
			++tempVarSlot;

			storeScratch(TypeKind.REFERENCE, functionObjSlot);
			stackTypes.removeLast();

			saveStackTempDesc(realizedMethodType.descriptor().parameterList());

			loadScratch(TypeKind.REFERENCE, functionObjSlot);
			stackTypes.add(realizedType.classDesc());

			restoreStackTempDesc(realizedMethodType.descriptor().parameterList());

			var resultType = realizedMethodType.descriptor().returnType();
			var stepResultType = resultType.nested("Step");

			var invokeDynamicSig = realizedMethodType.descriptor()
				.insertParameterTypes(0, realizedType.classDesc()).changeReturnType(stepResultType);

			var stepSig = MethodTypeDesc.of(resultType);

			var callSite = DynamicCallSiteDesc.of(
				MethodHandleDesc.ofMethod(
					DirectMethodHandleDesc.Kind.STATIC,
					ClassDesc.of("java.lang.invoke.LambdaMetafactory"),
					"metafactory",
					MethodTypeDesc.of(
						ClassDesc.of("java.lang.invoke.CallSite"),
						ClassDesc.of("java.lang.invoke.MethodHandles$Lookup"),
						CD_String,
						ClassDesc.of("java.lang.invoke.MethodType"),
						ClassDesc.of("java.lang.invoke.MethodType"),
						ClassDesc.of("java.lang.invoke.MethodHandle"),
						ClassDesc.of("java.lang.invoke.MethodType")
					)
				),
				"step",
				invokeDynamicSig,

				stepSig,
				MethodHandleDesc.ofMethod(
					DirectMethodHandleDesc.Kind.INTERFACE_VIRTUAL,
					realizedType.classDesc(),
					realizedType.methodName(),
					realizedMethodType.descriptor()
				),
				stepSig
			);

			cb.invokedynamic(callSite);
			emitResultReturn();

			tempVarSlot = functionObjSlot;

			stackTypes.clear();
			isUnreachable = true;
		}

		protected final void generateReturnCallIndirect(ControlInstr.Return_Call_Indirect instr) {
			var tableIdx = instr.table();
			var funcTypeIdx = instr.funcType();
			var tableInfo = tables.get(tableIdx.index());
			loadModuleInstance();
			cb.getfield(className, tableInfo.fieldName, wasmTable);
			cb.invokestatic(
				wasmTable,
				"table_get",
				MethodTypeDesc.of(CD_Object, addrDesc(tableInfo.tableType.addrType()), wasmTable)
			);

			var defType = types.get(funcTypeIdx.index());
			var realizedType = compiler.getDefType(defType);
			cb.checkcast(realizedType.classDesc());

			stackTypes.removeLast();
			stackTypes.add(realizedType.classDesc());

			generateReturnCallRef(new ControlInstr.Return_Call_Ref(funcTypeIdx));
		}

		private void unpackResultType(ResultType resultType, ClassDesc endResultClass) {
			if(resultType.types().isEmpty()) {
				cb.pop();
				return;
			}

			for(int i = 0; i < resultType.types().size(); ++i) {
				var t = compiler.getValType(resultType.types().get(i)).type();
				var erasedType = t.isPrimitive() ? t : CD_Object;

				stackTypes.add(stackType(t));

				boolean isLast = i == resultType.types().size() - 1;
				if(!isLast) {
					cb.dup();
				}

				cb.getfield(endResultClass, "item" + i, erasedType);
				if(!t.isPrimitive() && !t.equals(CD_Object)) {
					cb.checkcast(t);
				}

				if(!isLast) {
					if(slotSize(t) == 2) {
						cb.dup2_x1();
						cb.pop2();
					}
					else {
						cb.swap();
					}
				}
			}
		}

		protected final void generateResult() {
			var resultArgTypes = new ArrayList<ClassDesc>();

			for(var t : returnType.types()) {
				var realization = compiler.getValType(t);
				resultArgTypes.add(realization.type().isPrimitive() ? realization.type() : CD_Object);
			}

			var returnTypeClass = compiler.getResultType(returnType).classDesc();

			cb.invokestatic(returnTypeClass, "of", MethodTypeDesc.of(returnTypeClass, resultArgTypes), true);
		}

		protected final void generateReturn() {
			generateResult();
			emitResultReturn();

			isUnreachable = true;
			stackTypes.clear();
		}

		private void saveStackTempRes(ResultType types) {
			saveStackTempDesc(Lists.transform(types.types(), arg -> compiler.getValType(arg).type()));
		}

		private void saveStackTempDesc(List<ClassDesc> types) {
			saveStackTemp(types);
		}

		private void saveStackTemp(List<ClassDesc> tempTypes) {
			for(var t : tempTypes) {
				tempVarSlot += slotSize(t);
			}

			int slot = tempVarSlot;
			for(var t : tempTypes.reversed()) {
				slot -= slotSize(t);
				storeScratch(typeKind(t), slot);
				stackTypes.removeLast();
			}
		}

		private void restoreStackTempRes(ResultType types) {
			restoreStackTempDesc(Lists.transform(types.types(), arg -> compiler.getValType(arg).type()));
		}

		private void restoreStackTempDesc(List<ClassDesc> types) {
			restoreStackTemp(types);
		}

		private void restoreStackTemp(List<ClassDesc> tempTypes) {
			for(var t : tempTypes.reversed()) {
				tempVarSlot -= slotSize(t);
			}

			int slot = tempVarSlot;
			for(var t : tempTypes) {
				loadScratch(typeKind(t), slot);
				slot += slotSize(t);
				stackTypes.add(stackType(t));
			}
		}

	}

	private class BytecodeGenerator extends BytecodeGeneratorBase {
		BytecodeGenerator(
			CodeBuilder cb, LocalInfo[] locals, ResultType returnType, int moduleVarSlot, int tempVarSlot
		) {
			super(cb, returnType, tempVarSlot);
			this.locals = locals;
			this.moduleVarSlot = moduleVarSlot;
		}

		private final List<LabelInfo> labels = new ArrayList<>();
		private final Set<Label> jumpedLabels = new HashSet<>();
		private final LocalInfo[] locals;
		private final int moduleVarSlot;

		@Override
		protected ClassDesc localType(int index) {
			return locals[index].type();
		}

		@Override
		protected void loadLocal(int index) {
			var local = locals[index];
			cb.loadLocal(typeKind(local.type()), local.slotIndex());
		}

		@Override
		protected void storeLocal(int index) {
			var local = locals[index];
			cb.storeLocal(typeKind(local.type()), local.slotIndex());
		}

		@Override
		protected void loadModuleInstance() {
			cb.aload(moduleVarSlot);
		}

		@Override
		protected void loadScratch(TypeKind type, int index) {
			cb.loadLocal(type, index);
		}

		@Override
		protected void storeScratch(TypeKind type, int index) {
			cb.storeLocal(type, index);
		}

		@Override
		protected void emitResultReturn() {
			cb.areturn();
		}

		void generateInstructionBlock(Expr body) {
			for(var insn : body.body()) {
				generateInstruction(insn);

				if(isUnreachable) {
					break;
				}
			}
		}

		void generateFunctionBody(Expr body) {
			var returnLabel = cb.newLabel();
			labels.add(new LabelInfo(returnLabel, returnType));
			generateInstructionBlock(body);

			if(!isUnreachable || jumpedLabels.contains(returnLabel)) {
				cb.labelBinding(returnLabel);
				generateReturn();
			}
		}

		@Override
		protected void generateControlInstr(ControlInstr instr) {
			switch(instr) {
				case ControlInstr.Nop() -> generateNop();
				case ControlInstr.Unreachable() -> generateUnreachable();
				case ControlInstr.Block(var blockType, var innerBlock) -> {
					var type = closure.resolveFuncType(getBlockFuncType(blockType));

					var endLabel = cb.newLabel();

					var state = enterBlock(type);

					labels.add(new LabelInfo(endLabel, closure.resolveResultType(type.results())));
					generateInstructionBlock(new Expr(innerBlock));
					labels.removeLast();
					cb.labelBinding(endLabel);

					exitBlock(type, state, endLabel);
				}
				case ControlInstr.Loop(var blockType, var innerBlock) -> {
					var type = closure.resolveFuncType(getBlockFuncType(blockType));

					var restartLoopLabel = cb.newLabel();

					var state = enterBlock(type);

					cb.labelBinding(restartLoopLabel);
					labels.add(new LabelInfo(restartLoopLabel, closure.resolveResultType(type.args())));
					generateInstructionBlock(new Expr(innerBlock));
					labels.removeLast();

					if(!isUnreachable) {
						exitBlock(type, state, null);
					}
				}
				case ControlInstr.If(var blockType, var thenBody, var elseBody) -> {
					var type = closure.resolveFuncType(getBlockFuncType(blockType));

					var exitThenLabel = cb.newLabel();
					var elseLabel = cb.newLabel();
					var exitElseLabel = cb.newLabel();
					var endLabel = cb.newLabel();

					stackTypes.removeLast();
					cb.ifeq(elseLabel);

					var stackTypesCopy = new ArrayList<>(stackTypes);

					var state = enterBlock(type);
					labels.add(new LabelInfo(exitThenLabel, type.results()));
					generateInstructionBlock(new Expr(thenBody));
					labels.removeLast();
					cb.labelBinding(exitThenLabel);
					exitBlock(type, state, exitThenLabel);
					if(!isUnreachable)
						cb.goto_(endLabel);
					boolean thenUnreachable = isUnreachable;

					cb.labelBinding(elseLabel);
					isUnreachable = false;
					stackTypes.clear();
					stackTypes.addAll(stackTypesCopy);

					state = enterBlock(type);
					labels.add(new LabelInfo(exitElseLabel, type.results()));
					generateInstructionBlock(new Expr(elseBody));
					labels.removeLast();
					cb.labelBinding(exitElseLabel);
					exitBlock(type, state, exitElseLabel);
					isUnreachable &= thenUnreachable;

					cb.labelBinding(endLabel);
				}
				case ControlInstr.Throw throwInstr -> generateThrow(throwInstr);
				case ControlInstr.Throw_Ref() -> generateThrowRef();
				case ControlInstr.Br(var labelIdx) -> {
					var label = getLabel(labelIdx);
					jumpedLabels.add(label.label);

					if(jumpNeedsStackFix(label.labelType)) {
						fixJumpStack(label.labelType);
					}
					cb.goto_(label.label);

					isUnreachable = true;
					stackTypes.clear();
				}
				case ControlInstr.Br_If(var labelIdx) -> {
					var label = getLabel(labelIdx);
					jumpedLabels.add(label.label);
					stackTypes.removeLast();

					if(jumpNeedsStackFix(label.labelType)) {
						var endLabel = cb.newLabel();
						cb.ifeq(endLabel);
						fixJumpStack(label.labelType);
						cb.goto_(label.label);
						cb.labelBinding(endLabel);
					}
					else {
						cb.ifne(label.label);
					}
				}
				case ControlInstr.Br_Table(var table, var fallbackIdx) -> {
					if(table.isEmpty()) {
						cb.pop();
						stackTypes.removeLast();
						generateControlInstr(new ControlInstr.Br(fallbackIdx));
						return;
					}

					var fallbackLabel = getLabel(fallbackIdx);
					jumpedLabels.add(fallbackLabel.label);

					stackTypes.removeLast();

					if(jumpNeedsStackFix(fallbackLabel.labelType)) {
						int indexVarSlot = tempVarSlot;
						tempVarSlot += 1;

						storeScratch(TypeKind.INT, indexVarSlot);
						fixJumpStack(fallbackLabel.labelType);
						loadScratch(TypeKind.INT, indexVarSlot);

						tempVarSlot = indexVarSlot;
					}

					List<SwitchCase> cases = new ArrayList<>();
					for(int i = 0; i < table.size(); ++i) {
						var label = getLabel(table.get(i));
						jumpedLabels.add(label.label);
						cases.add(SwitchCase.of(i, label.label));
					}

					cb.tableswitch(0, table.size() - 1, fallbackLabel.label, cases);
					isUnreachable = true;
					stackTypes.clear();
				}
				case ControlInstr.Br_OnNull(var labelIdx) -> {
					var label = getLabel(labelIdx);
					jumpedLabels.add(label.label);

					cb.dup();
					var nonNullLabel = cb.newLabel();
					cb.ifnonnull(nonNullLabel);
					if(jumpNeedsStackFix(label.labelType)) {
						var top = stackTypes.getLast();
						stackTypes.removeLast();

						cb.pop();
						fixJumpStack(label.labelType);
						cb.goto_(label.label);

						stackTypes.add(top);
					}
					else {
						cb.pop();
						cb.goto_(label.label);
					}
					cb.labelBinding(nonNullLabel);
				}
				case ControlInstr.Br_OnNonNull(var labelIdx) -> {
					var label = getLabel(labelIdx);
					jumpedLabels.add(label.label);

					cb.dup();
					if(jumpNeedsStackFix(label.labelType)) {
						var nullLabel = cb.newLabel();
						cb.ifnull(nullLabel);
						fixJumpStack(label.labelType);
						cb.goto_(label.label);
						cb.labelBinding(nullLabel);
					}
					else {
						cb.ifnonnull(label.label);
					}
					cb.pop();
					stackTypes.removeLast();
				}
				case ControlInstr.Br_OnCast(var labelIdx, var _, var t2) -> {
					var label = getLabel(labelIdx);
					var refType = closure.resolveRefType(t2);
					var classType = compiler.getValType(refType);

					if(isBottomType(refType.heapType())) {
						if(t2.isNullable()) {
							jumpedLabels.add(label.label);

							var nonNullLabel = cb.newLabel();

							cb.dup();
							cb.ifnonnull(nonNullLabel);
							// Do this so the verifier knows the value is null.
							cb.pop();
							cb.aconst_null();
							cb.goto_(label.label);
							cb.labelBinding(nonNullLabel);
						}
						else {
							// The type is empty so the check always fails.
							cb.pop();
						}
					}
					else {
						jumpedLabels.add(label.label);

						var castFailLabel = cb.newLabel();
						var castSuccessLabel = cb.newLabel();

						if(t2.isNullable()) {
							cb.dup();
							cb.ifnull(castSuccessLabel);
						}

						cb.dup();
						cb.instanceOf(classType.type());
						cb.ifeq(castFailLabel);
						cb.labelBinding(castSuccessLabel);
						if(jumpNeedsStackFix(label.labelType)) {
							fixJumpStack(label.labelType);
						}
						cb.checkcast(classType.type());
						cb.goto_(label.label);

						cb.labelBinding(castFailLabel);
					}

				}
				case ControlInstr.Br_OnCastFail(var labelIdx, var _, var t2) -> {
					var label = getLabel(labelIdx);
					jumpedLabels.add(label.label);
					var classType = compiler.getValType(closure.resolveRefType(t2));

					if(isBottomType(t2.heapType())) {
						if(t2.isNullable()) {
							cb.dup();
							cb.ifnonnull(label.label);
							// Do this so the verifier knows the value is null.
							cb.pop();
							cb.aconst_null();
						}
						else {
							cb.goto_(label.label);
							stackTypes.clear();
							isUnreachable = true;
						}
					}
					else {
						var castSuccessLabel = cb.newLabel();

						if(t2.isNullable()) {
							cb.dup();
							cb.ifnull(castSuccessLabel);
						}

						cb.dup();
						cb.instanceOf(classType.type());
						if(jumpNeedsStackFix(label.labelType)) {
							cb.ifne(castSuccessLabel);
							fixJumpStack(label.labelType);
							cb.goto_(label.label);
						}
						else {
							cb.ifeq(label.label);
						}
						cb.labelBinding(castSuccessLabel);
						cb.checkcast(classType.type());
					}
					if(!isUnreachable) {
						stackTypes.set(stackTypes.size() - 1, classType.type());
					}
				}

				case ControlInstr.Return() -> generateReturn();

				case ControlInstr.Call call -> generateCall(call);
				case ControlInstr.Call_Ref call -> generateCallRef(call);
				case ControlInstr.Call_Indirect call -> generateCallIndirect(call);
				case ControlInstr.Return_Call call -> generateReturnCall(call);
				case ControlInstr.Return_Call_Ref call -> generateReturnCallRef(call);
				case ControlInstr.Return_Call_Indirect call -> generateReturnCallIndirect(call);
				case ControlInstr.Try_Table(var blockType, var catchClauses, var innerBlock) -> {
					var type = closure.resolveFuncType(getBlockFuncType(blockType));

					var tryStartLabel = cb.newLabel();
					var tryEndLabel = cb.newLabel();
					var endLabel = cb.newLabel();

					cb.labelBinding(tryStartLabel);
					cb.nop(); // Ensure that the try block is non-empty.

					var state = enterBlock(type);
					labels.add(new LabelInfo(endLabel, closure.resolveResultType(type.results())));
					generateInstructionBlock(new Expr(innerBlock));
					labels.removeLast();
					cb.labelBinding(tryEndLabel);
					exitBlock(type, state, endLabel);
					if(!isUnreachable)
						cb.goto_(endLabel);

					for(var catchClause : catchClauses) {
						switch(catchClause) {
							case ControlInstr.CatchTag(var tagIdx, var labelIdx) -> {
								var tagInfo = tags.get(tagIdx.index());

								var catchLabel = cb.newLabel();
								cb.labelBinding(catchLabel);

								var types = tagInfo.funcType.args().types();
								if(types.isEmpty()) {
									cb.pop();
								}
								else {
									storeScratch(TypeKind.REFERENCE, tempVarSlot);

									for(int i = 0; i < types.size(); ++i) {
										loadScratch(TypeKind.REFERENCE, tempVarSlot);
										cb.getfield(
											tagInfo.realization.classDesc(),
											"item" + i,
											compiler.getValType(types.get(i)).type()
										);
									}
								}

								var targetLabel = getLabel(labelIdx).label;
								cb.goto_(targetLabel);
								jumpedLabels.add(targetLabel);

								cb.exceptionCatch(
									tryStartLabel,
									tryEndLabel,
									catchLabel,
									tagInfo.realization.classDesc()
								);
							}
							case ControlInstr.CatchTagRef(var tagIdx, var labelIdx) -> {
								var tagInfo = tags.get(tagIdx.index());

								var catchLabel = cb.newLabel();
								cb.labelBinding(catchLabel);

								var types = tagInfo.funcType.args().types();
								if(!types.isEmpty()) {
									storeScratch(TypeKind.REFERENCE, tempVarSlot);

									for(int i = 0; i < types.size(); ++i) {
										loadScratch(TypeKind.REFERENCE, tempVarSlot);
										cb.getfield(
											tagInfo.realization.classDesc(),
											"item" + i,
											compiler.getValType(types.get(i)).type()
										);
									}

									loadScratch(TypeKind.REFERENCE, tempVarSlot);
								}

								var targetLabel = getLabel(labelIdx).label;
								cb.goto_(targetLabel);
								jumpedLabels.add(targetLabel);

								cb.exceptionCatch(
									tryStartLabel,
									tryEndLabel,
									catchLabel,
									tagInfo.realization.classDesc()
								);
							}
							case ControlInstr.CatchAll(var labelIdx) -> {
								var exType = ClassDesc.of(RUNTIME_PACKAGE, "WebAssemblyException");

								var catchLabel = cb.newLabel();
								cb.labelBinding(catchLabel);

								cb.pop();
								var targetLabel = getLabel(labelIdx).label;
								cb.goto_(targetLabel);
								jumpedLabels.add(targetLabel);
								cb.exceptionCatch(tryStartLabel, tryEndLabel, catchLabel, exType);
							}
							case ControlInstr.CatchAllRef(var labelIdx) -> {
								var exType = ClassDesc.of(RUNTIME_PACKAGE, "WebAssemblyException");

								var catchLabel = cb.newLabel();
								cb.labelBinding(catchLabel);

								var targetLabel = getLabel(labelIdx).label;
								cb.goto_(targetLabel);
								jumpedLabels.add(targetLabel);
								cb.exceptionCatch(tryStartLabel, tryEndLabel, catchLabel, exType);
							}
						}
					}

					cb.labelBinding(endLabel);

				}
			}
		}

		private LabelInfo getLabel(LabelIdx labelIdx) {
			return labels.get(labels.size() - 1 - labelIdx.index());
		}

		private record LabelInfo(Label label, ResultType labelType) {}

		private record StashedBlockState(int oldTempVarSlot, List<StashedStackValue> stashedStackValues) {}
		private record StashedStackValue(int slot, ClassDesc type) {}

		private StashedBlockState enterBlock(FuncType type) {
			if(stackTypes.size() == type.args().types().size()) {
				stackTypes.clear();
				for(var arg : type.args().types()) {
					stackTypes.add(stackType(compiler.getValType(arg).type()));
				}
				return new StashedBlockState(tempVarSlot, List.of());
			}

			int oldTempVarSlot = tempVarSlot;
			int stashCount = stackTypes.size() - type.args().types().size();

			List<StashedStackValue> stashedStackValues = new ArrayList<>();
			for(var t : stackTypes) {
				stashedStackValues.add(new StashedStackValue(tempVarSlot, t));
				tempVarSlot += slotSize(t);
			}

			for(var value : stashedStackValues.reversed()) {
				storeScratch(typeKind(value.type), value.slot);
			}

			var stashedArgs = stashedStackValues.subList(stashCount, stashedStackValues.size());
			for(var stashedArg : stashedArgs) {
				loadScratch(typeKind(stashedArg.type), stashedArg.slot);

				// A bit odd because we can't actually discard the variables until
				// after all instructions from the loop have run.
				// But this value won't matter until after the loop anyway.
				tempVarSlot -= slotSize(stashedArg.type);
			}

			stashedArgs.clear();
			stackTypes.clear();
			for(var arg : type.args().types()) {
				stackTypes.add(stackType(compiler.getValType(arg).type()));
			}

			return new StashedBlockState(oldTempVarSlot, stashedStackValues);
		}

		private void exitBlock(FuncType type, StashedBlockState state, @Nullable Label endLabel) {
			if(endLabel != null) {
				if(jumpedLabels.contains(endLabel)) {
					isUnreachable = false;
				}
			}

			if(isUnreachable) {
				return;
			}

			stackTypes.clear();

			if(state.stashedStackValues.isEmpty()) {
				for(var t : type.results().types()) {
					stackTypes.add(stackType(compiler.getValType(t).type()));
				}
			}
			else {
				var stashedValues = new ArrayList<>(state.stashedStackValues);
				for(var resType : type.results().types()) {
					var javaType = compiler.getValType(resType).type();
					var t = stackType(javaType);
					stashedValues.add(new StashedStackValue(tempVarSlot, t));
					tempVarSlot += slotSize(t);
				}

				for(var resStash : stashedValues.subList(state.stashedStackValues.size(), stashedValues.size())
					.reversed()) {
					storeScratch(typeKind(resStash.type), resStash.slot);
				}

				for(var stashedValue : stashedValues) {
					loadScratch(typeKind(stashedValue.type), stashedValue.slot);
					stackTypes.add(stashedValue.type);
				}

				tempVarSlot = state.oldTempVarSlot;
			}

		}

		private boolean jumpNeedsStackFix(ResultType labelType) {
			return stackTypes.size() != labelType.types().size();
		}

		private void fixJumpStack(ResultType labelType) {
			fixJumpStack(labelType.types().size());
		}

		private void fixJumpStack(int resultSize) {
			int oldTempVarSlot = tempVarSlot;
			var stashedValues = new ArrayList<StashedStackValue>();
			for(var t : stackTypes.subList(stackTypes.size() - resultSize, stackTypes.size())) {
				stashedValues.add(new StashedStackValue(tempVarSlot, t));
				tempVarSlot += slotSize(t);
			}

			var localStackTypes = new ArrayList<>(stackTypes);

			for(var resStash : stashedValues.reversed()) {
				storeScratch(typeKind(resStash.type), resStash.slot);
				localStackTypes.removeLast();
			}

			while (!localStackTypes.isEmpty()) {
				var t = localStackTypes.getLast();
				localStackTypes.removeLast();

				if(slotSize(t) == 2) {
					cb.pop2();
				}
				else {
					cb.pop();
				}
			}

			for(var stashedValue : stashedValues) {
				loadScratch(typeKind(stashedValue.type), stashedValue.slot);
				localStackTypes.add(stashedValue.type);
			}

			tempVarSlot = oldTempVarSlot;
		}


	}

	private class FallbackFunctionClassGenerator extends WasmClassGenerator {
		FallbackFunctionClassGenerator(int functionIndex, MethodTypeDesc type, Func func) {
			super(ModuleClassGenerator.this.compiler);
			this.functionIndex = functionIndex;
			this.type = type;
			this.func = func;
		}
		private final int functionIndex;
		private final MethodTypeDesc type;
		private final Func func;
		private byte @Nullable [] cachedBytecode;

		private String localFieldName(int index) {
			var prefix = "local" + index;
			var names = module.name();
			if(names == null) {
				return prefix;
			}
			var localNames = names.localNames().get(functionIndex);
			if(localNames == null) {
				return prefix;
			}
			var name = localNames.get(index);
			return name == null ? prefix : prefix + "_" + NameMangling.sanitizeName(name);
		}

		@Override
		public ClassDesc className() {
			return ClassDesc.of(compiler.getOptions().javaPackage(), simpleClassName + "_SplitFunc_" + functionIndex);
		}

		@Override
		public ClassHierarchyResolver.ClassHierarchyInfo hierarchyInfo() {
			return ClassHierarchyResolver.ClassHierarchyInfo.ofClass(CD_Object);
		}

		@Override
		byte[] generateImpl() {
			if(cachedBytecode == null) {
				cachedBytecode = compiler.classFile().build(className(), clb -> {
					clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL);
					clb.withSuperclass(CD_Object);
					clb.withField("module", className, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);
					clb.withField("result", type.returnType(), ClassFile.ACC_PRIVATE);
					for(int i = 0; i < type.parameterCount(); ++i) {
						clb.withField(localFieldName(i), type.parameterType(i), 0);
					}
					for(int i = 0; i < func.locals().size(); ++i) {
						var localType = compiler.getValType(closure.resolveValType(func.locals().get(i))).type();
						clb.withField(localFieldName(type.parameterCount() + i), localType, 0);
					}

					clb.withMethodBody(
						"<init>",
						invokeMethodType(type).changeReturnType(CD_void),
						ClassFile.ACC_PUBLIC,
						cb -> {
							cb.aload(0).invokespecial(CD_Object, "<init>", MethodTypeDesc.of(CD_void));
							int slot = 1;
							for(int i = 0; i < type.parameterCount(); ++i) {
								var paramType = type.parameterType(i);
								cb.aload(0).loadLocal(typeKind(paramType), slot);
								cb.putfield(className(), localFieldName(i), paramType);
								slot += slotSize(paramType);
							}
							cb.aload(0).aload(slot).putfield(className(), "module", className);
							for(int i = 0; i < func.locals().size(); ++i) {
								if(closure.resolveValType(func.locals().get(i)).equals(VecType.V128)) {
									cb.aload(0);
									loadV128(cb, V128.ZERO);
									cb.putfield(className(), localFieldName(type.parameterCount() + i), v128Type);
								}
							}
							cb.return_();
						}
					);

					var states = new ArrayList<FallbackFunctionBodyCallback>();
					var queue = new ArrayDeque<FallbackFunctionBodyCallback>();
					queue.add(new FallbackFunctionBodyCallback(this, func.body().body()));
					int nextState = 1;
					while (!queue.isEmpty()) {
						var callback = queue.removeFirst();
						states.add(callback);
						for(var field : callback.stashedStackFields()) {
							clb.withField(field.name(), field.type(), ClassFile.ACC_PRIVATE);
						}
						callback.firstQueuedState = nextState;
						clb.withMethodBody(callback.name(), MethodTypeDesc.of(CD_int), ClassFile.ACC_PRIVATE, callback);
						for(var field : callback.additionalFields) {
							clb.withField(field.name(), field.type(), ClassFile.ACC_PRIVATE);
						}
						nextState += callback.queuedSplitMethods.size();
						callback.enqueueSplitMethods(queue);
					}

					clb.withMethodBody("execute", MethodTypeDesc.of(type.returnType()), ClassFile.ACC_PUBLIC, cb -> {
						var dispatch = cb.newLabel();
						var completed = cb.newLabel();
						var invalidState = cb.newLabel();
						var cases = new ArrayList<SwitchCase>();
						for(var state : states) {
							cases.add(SwitchCase.of(state.currentState, cb.newLabel()));
						}

						cb.iconst_0().istore(1);
						cb.labelBinding(dispatch).iload(1).iconst_m1().if_icmpeq(completed);
						cb.iload(1).lookupswitch(invalidState, cases);
						for(int i = 0; i < states.size(); ++i) {
							cb.labelBinding(cases.get(i).target());
							cb.aload(0).invokevirtual(className(), states.get(i).name(), MethodTypeDesc.of(CD_int));
							cb.istore(1).goto_(dispatch);
						}

						cb.labelBinding(completed).aload(0).getfield(className(), "result", type.returnType());
						cb.invokestatic(
							ClassDesc.of("java.util.Objects"),
							"requireNonNull",
							MethodTypeDesc.of(CD_Object, CD_Object)
						);
						cb.checkcast(type.returnType()).areturn();

						var assertionError = ClassDesc.of("java.lang.AssertionError");
						cb.labelBinding(invalidState).new_(assertionError).dup().iload(1);
						cb.invokespecial(assertionError, "<init>", MethodTypeDesc.of(CD_void, CD_int)).athrow();
					});
				});
			}
			return cachedBytecode;
		}
	}

	private record StashedStackField(String name, ClassDesc type) {}

	private List<ClassDesc> fallbackStackTypes(ResultType resultType) {
		return resultType.types().stream()
			.map(t -> BytecodeGeneratorBase.stackType(compiler.getValType(closure.resolveValType(t)).type())).toList();
	}

	private record FallbackControlTarget(
		@Nullable FallbackFunctionBodyCallback state,
		int prefixDepth,
		List<ClassDesc> arguments
	) {}

	private record FallbackTryRegion(
		List<StashedStackField> prefix,
		List<ControlInstr.CatchClause> catches,
		List<FallbackControlTarget> labels
	) {}

	private class FallbackFunctionBodyCallback implements Consumer<CodeBuilder> {
		FallbackFunctionBodyCallback(FallbackFunctionClassGenerator helper, List<Instr> instrs) {
			this(helper, List.copyOf(instrs), List.of(), 0, List.of(), null, List.of());
			var results = closure.resolveResultType(getFuncType(types.get(helper.func.type().index())).results());
			completion = new FallbackControlTarget(null, 0, fallbackStackTypes(results));
			labels = List.of(completion);
		}

		private FallbackFunctionBodyCallback(
			FallbackFunctionClassGenerator helper, List<Instr> instrs, List<StashedStackField> stashedStack,
			int currentState, List<FallbackControlTarget> labels, @Nullable FallbackControlTarget completion,
			List<FallbackTryRegion> exceptionRegions
		) {
			this.helper = helper;
			this.instrs = instrs;
			this.stashedStack = List.copyOf(stashedStack);
			this.currentState = currentState;
			this.labels = List.copyOf(labels);
			this.completion = completion;
			this.exceptionRegions = List.copyOf(exceptionRegions);
		}

		private final FallbackFunctionClassGenerator helper;
		private final List<Instr> instrs;
		private final List<StashedStackField> stashedStack;
		private final int currentState;
		private List<FallbackControlTarget> labels;
		private @Nullable FallbackControlTarget completion;
		private List<FallbackTryRegion> exceptionRegions;
		// The class generator reserves ids before emission; retries use the same seed.
		private int firstQueuedState;
		private int instructionIndex;
		private final List<FallbackFunctionBodyCallback> queuedSplitMethods = new ArrayList<>();
		private final List<StashedStackField> additionalFields = new ArrayList<>();

		String name() {
			return "chunk" + currentState;
		}

		List<StashedStackField> stashedStackFields() {
			return stashedStack;
		}

		void enqueueSplitMethods(Deque<FallbackFunctionBodyCallback> queue) {
			queue.addAll(queuedSplitMethods);
		}

		private FallbackFunctionBodyCallback queueState(
			List<Instr> body,
			List<ClassDesc> stack,
			List<FallbackControlTarget> labels,
			FallbackControlTarget completion,
			List<FallbackTryRegion> regions
		) {
			int state = firstQueuedState + queuedSplitMethods.size();
			var fields = new ArrayList<StashedStackField>();
			for(var type : stack) {
				fields.add(new StashedStackField("state" + state + "_stack" + fields.size(), type));
			}
			var result = new FallbackFunctionBodyCallback(helper, body, fields, state, labels, completion, regions);
			queuedSplitMethods.add(result);
			return result;
		}

		private List<Instr> remainingInstructions() {
			return instrs.subList(instructionIndex + 1, instrs.size());
		}

		@Override
		public void accept(CodeBuilder codeBuilder) {
			queuedSplitMethods.clear();
			additionalFields.clear();
			instructionIndex = 0;
			var codeSizeTransform = new CodeSizeEstimator();
			codeBuilder.transforming(codeSizeTransform, cb2 -> {
				var gen = new FallbackFunctionBodyGenerator(cb2, this, helper);
				var protectedStart = cb2.newLabel();
				var protectedEnd = cb2.newLabel();
				cb2.labelBinding(protectedStart).nop();
				gen.restoreFields(stashedStack, true);
				for(; instructionIndex < instrs.size(); ++instructionIndex) {
					if(instructionIndex > 0 && instrs.get(instructionIndex) instanceof ControlInstr.Br_Table branchTable
						&& codeSizeTransform.estimatedSize() + gen.estimateBranchTableSize(branchTable) > 50000) {
						var next = queueState(
							instrs.subList(instructionIndex, instrs.size()),
							List.copyOf(gen.stackTypes),
							labels,
							Objects.requireNonNull(completion),
							exceptionRegions
						);
						gen.transfer(new FallbackControlTarget(next, 0, List.copyOf(gen.stackTypes)));
						break;
					}
					gen.generateInstruction(instrs.get(instructionIndex));
					if(gen.isUnreachable) {
						break;
					}
					if(instructionIndex + 1 < instrs.size() && (codeSizeTransform.estimatedSize() > 50000
						|| (codeSizeTransform.estimatedSize() > 30000 && gen.stackTypes.isEmpty()))) {
						var next = queueState(
							remainingInstructions(),
							List.copyOf(gen.stackTypes),
							labels,
							Objects.requireNonNull(completion),
							exceptionRegions
						);
						gen.transfer(new FallbackControlTarget(next, 0, List.copyOf(gen.stackTypes)));
						break;
					}
				}
				if(!gen.isUnreachable) {
					gen.transfer(Objects.requireNonNull(completion));
				}
				cb2.labelBinding(protectedEnd);
				gen.generateCatchHandlers(protectedStart, protectedEnd);
			});
		}
	}

	private class FallbackFunctionBodyGenerator extends BytecodeGeneratorBase {
		FallbackFunctionBodyGenerator(
			CodeBuilder cb, FallbackFunctionBodyCallback callback, FallbackFunctionClassGenerator helper
		) {
			super(
				cb,
				closure.resolveResultType(getFuncType(types.get(helper.func.type().index())).results()),
				1
			);
			this.callback = callback;
			this.helper = helper;
		}

		private final FallbackFunctionBodyCallback callback;
		private final FallbackFunctionClassGenerator helper;

		@Override
		protected ClassDesc localType(int index) {
			if(index < helper.type.parameterCount()) {
				return helper.type.parameterType(index);
			}
			var local = helper.func.locals().get(index - helper.type.parameterCount());
			return compiler.getValType(closure.resolveValType(local)).type();
		}

		@Override
		protected void loadLocal(int index) {
			cb.aload(0).getfield(helper.className(), helper.localFieldName(index), localType(index));
		}

		@Override
		protected void storeLocal(int index) {
			var localType = localType(index);
			cb.aload(0);
			if(slotSize(localType) == 2) {
				cb.dup_x2().pop();
			}
			else {
				cb.swap();
			}
			cb.putfield(helper.className(), helper.localFieldName(index), localType);
		}

		@Override
		protected void loadModuleInstance() {
			cb.aload(0).getfield(helper.className(), "module", className);
		}

		@Override
		protected void loadScratch(TypeKind type, int index) {
			cb.loadLocal(type, index);
		}

		@Override
		protected void storeScratch(TypeKind type, int index) {
			cb.storeLocal(type, index);
		}

		@Override
		protected void emitResultReturn() {
			cb.aload(0).swap().putfield(helper.className(), "result", helper.type.returnType());
			clearExitedRegions(null);
			cb.iconst_m1().ireturn();
		}

		private void restoreFields(List<StashedStackField> fields, boolean clear) {
			for(var field : fields) {
				cb.aload(0).getfield(helper.className(), field.name(), field.type());
				if(clear && !field.type().isPrimitive()) {
					cb.aload(0).aconst_null().putfield(helper.className(), field.name(), field.type());
				}
				stackTypes.add(field.type());
			}
		}

		private void stashField(StashedStackField field) {
			var sourceType = stackTypes.removeLast();
			if(!field.type().isPrimitive() && !field.type().equals(sourceType)) {
				cb.checkcast(field.type());
			}
			cb.aload(0);
			if(slotSize(field.type()) == 2) {
				cb.dup_x2().pop();
			}
			else {
				cb.swap();
			}
			cb.putfield(helper.className(), field.name(), field.type());
		}

		private void discardTop() {
			if(slotSize(stackTypes.removeLast()) == 2) {
				cb.pop2();
			}
			else {
				cb.pop();
			}
		}

		private void normalizeStack(FallbackControlTarget target) {
			var fields = Objects.requireNonNull(target.state()).stashedStack;
			for(int i = fields.size() - 1; i >= target.prefixDepth(); --i) {
				stashField(fields.get(i));
			}
			while (stackTypes.size() > target.prefixDepth()) {
				discardTop();
			}
			for(int i = target.prefixDepth() - 1; i >= 0; --i) {
				stashField(fields.get(i));
			}
		}

		private void clearExitedRegions(@Nullable FallbackFunctionBodyCallback destination) {
			for(var region : callback.exceptionRegions) {
				if(destination == null || !destination.exceptionRegions.contains(region)) {
					for(var field : region.prefix()) {
						if(!field.type().isPrimitive()) {
							cb.aload(0).aconst_null().putfield(helper.className(), field.name(), field.type());
						}
					}
				}
			}
		}

		private void transfer(FallbackControlTarget target) {
			var destination = target.state();
			if(destination == null) {
				generateResult();
				for(var _ : target.arguments()) {
					stackTypes.removeLast();
				}
				stackTypes.add(helper.type.returnType());
				stashField(new StashedStackField("result", helper.type.returnType()));
				while (!stackTypes.isEmpty()) {
					discardTop();
				}
				clearExitedRegions(null);
				cb.iconst_m1().ireturn();
			}
			else {
				normalizeStack(target);
				clearExitedRegions(destination);
				cb.loadConstant(destination.currentState).ireturn();
			}
			isUnreachable = true;
		}

		private FallbackControlTarget controlTarget(LabelIdx index) {
			return callback.labels.get(callback.labels.size() - 1 - index.index());
		}

		private long estimateBranchTableSize(ControlInstr.Br_Table branchTable) {
			var table = branchTable.labels();
			var fallback = branchTable.fallback();
			// Switch opcode, header, maximum alignment padding, and entries.
			long size = table.isEmpty() ? 6 : 16L + 4L * table.size();
			var targets = new HashSet<>(table);
			targets.add(fallback);
			for(var index : targets) {
				var target = controlTarget(index);
				// The selector is consumed by the switch. Discard each unused stack value.
				size += stackTypes.size() - 1L - target.prefixDepth() - target.arguments().size();
				var destination = target.state();
				if(destination == null) {
					// Create the Result, store it, and return -1.
					size += 10;
				}
				else {
					for(var field : destination.stashedStack) {
						// Load this, rearrange the value, putfield, and allow a reference cast.
						size += 4 + (slotSize(field.type()) == 2 ? 2 : 1)
							+ (field.type().isPrimitive() ? 0 : 3);
					}
					// Load the state (allowing ldc_w) and return it.
					size += 4;
				}
				for(var region : callback.exceptionRegions) {
					if(destination == null || !destination.exceptionRegions.contains(region)) {
						for(var field : region.prefix()) {
							if(!field.type().isPrimitive()) {
								size += 5; // Load this, load null, putfield.
							}
						}
					}
				}
			}
			return size;
		}

		private void restoreTracking(List<ClassDesc> stack) {
			stackTypes.clear();
			stackTypes.addAll(stack);
			isUnreachable = false;
		}

		private FallbackFunctionBodyCallback continuation(FuncType type, int prefixDepth) {
			var entryStack = new ArrayList<>(stackTypes.subList(0, prefixDepth));
			entryStack.addAll(fallbackStackTypes(type.results()));
			return callback.queueState(
				callback.remainingInstructions(),
				entryStack,
				callback.labels,
				Objects.requireNonNull(callback.completion),
				callback.exceptionRegions
			);
		}

		private FallbackFunctionBodyCallback blockBody(
			List<Instr> instructions,
			FuncType type,
			int prefixDepth,
			FallbackFunctionBodyCallback continuation,
			boolean loop
		) {
			var entryStack = new ArrayList<>(stackTypes.subList(0, prefixDepth));
			entryStack.addAll(fallbackStackTypes(type.args()));
			var finish = new FallbackControlTarget(continuation, prefixDepth, fallbackStackTypes(type.results()));
			var body = callback
				.queueState(instructions, entryStack, callback.labels, finish, callback.exceptionRegions);
			var labels = new ArrayList<>(callback.labels);
			labels.add(loop ? new FallbackControlTarget(body, prefixDepth, fallbackStackTypes(type.args())) : finish);
			body.labels = List.copyOf(labels);
			return body;
		}

		private void enterBody(FallbackFunctionBodyCallback body, FuncType type, int prefixDepth) {
			transfer(new FallbackControlTarget(body, prefixDepth, fallbackStackTypes(type.args())));
		}

		@Override
		protected void generateControlInstr(ControlInstr instr) {
			switch(instr) {
				case ControlInstr.Block(var blockType, var instructions) -> {
					var type = closure.resolveFuncType(getBlockFuncType(blockType));
					int prefix = stackTypes.size() - type.args().types().size();
					var after = continuation(type, prefix);
					enterBody(blockBody(instructions, type, prefix, after, false), type, prefix);
				}
				case ControlInstr.Loop(var blockType, var instructions) -> {
					var type = closure.resolveFuncType(getBlockFuncType(blockType));
					int prefix = stackTypes.size() - type.args().types().size();
					var after = continuation(type, prefix);
					enterBody(blockBody(instructions, type, prefix, after, true), type, prefix);
				}
				case ControlInstr.If(var blockType, var thenInstructions, var elseInstructions) -> {
					var type = closure.resolveFuncType(getBlockFuncType(blockType));
					stackTypes.removeLast();
					int prefix = stackTypes.size() - type.args().types().size();
					var stack = List.copyOf(stackTypes);
					var after = continuation(type, prefix);
					var thenBody = blockBody(thenInstructions, type, prefix, after, false);
					var elseBody = blockBody(elseInstructions, type, prefix, after, false);
					var elseLabel = cb.newLabel();
					cb.ifeq(elseLabel);
					enterBody(thenBody, type, prefix);
					cb.labelBinding(elseLabel);
					restoreTracking(stack);
					enterBody(elseBody, type, prefix);
				}
				case ControlInstr.Br(var index) -> transfer(controlTarget(index));
				case ControlInstr.Br_If(var index) -> {
					stackTypes.removeLast();
					var stack = List.copyOf(stackTypes);
					var untaken = cb.newLabel();
					cb.ifeq(untaken);
					transfer(controlTarget(index));
					cb.labelBinding(untaken);
					restoreTracking(stack);
				}
				case ControlInstr.Br_Table(var table, var fallback) -> {
					stackTypes.removeLast();
					var stack = List.copyOf(stackTypes);
					var targets = new LinkedHashMap<LabelIdx, Label>();
					targets.put(fallback, cb.newLabel());
					var cases = new ArrayList<SwitchCase>();
					for(int i = 0; i < table.size(); ++i) {
						cases.add(SwitchCase.of(i, targets.computeIfAbsent(table.get(i), _ -> cb.newLabel())));
					}
					if(table.isEmpty()) {
						cb.pop().goto_(targets.get(fallback));
					}
					else {
						cb.tableswitch(0, table.size() - 1, targets.get(fallback), cases);
					}
					for(var target : targets.entrySet()) {
						cb.labelBinding(target.getValue());
						restoreTracking(stack);
						transfer(controlTarget(target.getKey()));
					}
				}
				case ControlInstr.Br_OnNull(var index) -> {
					var stack = List.copyOf(stackTypes);
					var nonNull = cb.newLabel();
					cb.dup().ifnonnull(nonNull);
					discardTop();
					transfer(controlTarget(index));
					cb.labelBinding(nonNull);
					restoreTracking(stack);
				}
				case ControlInstr.Br_OnNonNull(var index) -> {
					var stack = List.copyOf(stackTypes);
					var nullLabel = cb.newLabel();
					cb.dup().ifnull(nullLabel);
					transfer(controlTarget(index));
					cb.labelBinding(nullLabel);
					restoreTracking(stack);
					discardTop();
				}
				case ControlInstr.Br_OnCast(var index, var _, var targetType) -> {
					var stack = List.copyOf(stackTypes);
					var failed = cb.newLabel();
					cb.dup();
					stackTypes.add(stackTypes.getLast());
					generateInstruction(new ReferenceInstr.Ref_Test(targetType));
					stackTypes.removeLast();
					cb.ifeq(failed);
					generateInstruction(new ReferenceInstr.Ref_Cast(targetType));
					transfer(controlTarget(index));
					cb.labelBinding(failed);
					restoreTracking(stack);
				}
				case ControlInstr.Br_OnCastFail(var index, var _, var targetType) -> {
					var stack = List.copyOf(stackTypes);
					var success = cb.newLabel();
					cb.dup();
					stackTypes.add(stackTypes.getLast());
					generateInstruction(new ReferenceInstr.Ref_Test(targetType));
					stackTypes.removeLast();
					cb.ifne(success);
					transfer(controlTarget(index));
					cb.labelBinding(success);
					restoreTracking(stack);
					generateInstruction(new ReferenceInstr.Ref_Cast(targetType));
				}
				case ControlInstr.Return() -> transfer(callback.labels.getFirst());
				case ControlInstr.Try_Table(var blockType, var catches, var instructions) -> {
					var type = closure.resolveFuncType(getBlockFuncType(blockType));
					int prefix = stackTypes.size() - type.args().types().size();
					var after = continuation(type, prefix);
					var body = blockBody(instructions, type, prefix, after, false);
					var savedPrefix = new ArrayList<StashedStackField>();
					for(int i = 0; i < prefix; ++i) {
						var field = new StashedStackField("try" + body.currentState + "_stack" + i, stackTypes.get(i));
						savedPrefix.add(field);
						callback.additionalFields.add(field);
					}
					var regions = new ArrayList<>(callback.exceptionRegions);
					regions.add(new FallbackTryRegion(List.copyOf(savedPrefix), List.copyOf(catches), callback.labels));
					body.exceptionRegions = List.copyOf(regions);
					normalizeStack(new FallbackControlTarget(body, prefix, fallbackStackTypes(type.args())));
					for(int i = 0; i < prefix; ++i) {
						var source = body.stashedStack.get(i);
						var destination = savedPrefix.get(i);
						cb.aload(0).aload(0).getfield(helper.className(), source.name(), source.type());
						cb.putfield(helper.className(), destination.name(), destination.type());
					}
					cb.loadConstant(body.currentState).ireturn();
					isUnreachable = true;
				}
				case ControlInstr.Nop() -> generateNop();
				case ControlInstr.Unreachable() -> generateUnreachable();
				case ControlInstr.Throw throwInstr -> generateThrow(throwInstr);
				case ControlInstr.Throw_Ref() -> generateThrowRef();
				case ControlInstr.Call call -> generateCall(call);
				case ControlInstr.Call_Ref call -> generateCallRef(call);
				case ControlInstr.Call_Indirect call -> generateCallIndirect(call);
				case ControlInstr.Return_Call call -> generateReturnCall(call);
				case ControlInstr.Return_Call_Ref call -> generateReturnCallRef(call);
				case ControlInstr.Return_Call_Indirect call -> generateReturnCallIndirect(call);
			}
		}

		private void generateCatchHandlers(Label start, Label end) {
			for(var region : callback.exceptionRegions.reversed()) {
				for(var clause : region.catches()) {
					ClassDesc exceptionType;
					LabelIdx targetIndex;
					@Nullable
					TagInfo tag;
					boolean includeReference;
					switch(clause) {
						case ControlInstr.CatchTag(var tagIndex, var index) -> {
							tag = tags.get(tagIndex.index());
							exceptionType = tag.realization.classDesc();
							targetIndex = index;
							includeReference = false;
						}
						case ControlInstr.CatchTagRef(var tagIndex, var index) -> {
							tag = tags.get(tagIndex.index());
							exceptionType = tag.realization.classDesc();
							targetIndex = index;
							includeReference = true;
						}
						case ControlInstr.CatchAll(var index) -> {
							tag = null;
							exceptionType = ClassDesc.of(RUNTIME_PACKAGE, "WebAssemblyException");
							targetIndex = index;
							includeReference = false;
						}
						case ControlInstr.CatchAllRef(var index) -> {
							tag = null;
							exceptionType = ClassDesc.of(RUNTIME_PACKAGE, "WebAssemblyException");
							targetIndex = index;
							includeReference = true;
						}
					}
					var handler = cb.newLabel();
					cb.exceptionCatch(start, end, handler, exceptionType);
					cb.labelBinding(handler).astore(tempVarSlot);
					restoreTracking(List.of());
					restoreFields(region.prefix(), false);
					if(tag != null) {
						for(int i = 0; i < tag.funcType.args().types().size(); ++i) {
							var fieldType = compiler.getValType(tag.funcType.args().types().get(i)).type();
							cb.aload(tempVarSlot).getfield(exceptionType, "item" + i, fieldType);
							stackTypes.add(stackType(fieldType));
						}
					}
					if(includeReference) {
						cb.aload(tempVarSlot);
						stackTypes.add(exceptionType);
					}
					transfer(region.labels().get(region.labels().size() - 1 - targetIndex.index()));
				}
			}
		}

	}

	private FuncType getFuncType(DefType t) {
		var subtype = TypeUnroll.unroll(t);
		return switch(subtype.compositeType()) {
			case AggregateType _ -> throw new RuntimeException("Unexpected aggregate elementType");
			case FuncType ft -> ft;
		};
	}

	private ArrayType getArrayType(DefType t) {
		var subtype = TypeUnroll.unroll(t);
		return switch(subtype.compositeType()) {
			case ArrayType arrayType -> arrayType;
			default -> throw new RuntimeException("Expected an array type");
		};
	}

	private StructType getStructType(DefType t) {
		var subtype = TypeUnroll.unroll(t);
		return switch(subtype.compositeType()) {
			case StructType structType -> structType;
			default -> throw new RuntimeException("Expected a struct type");
		};
	}

	private FuncType getBlockFuncType(ControlInstr.BlockType t) {
		return switch(t) {
			case ControlInstr.BlockType.Empty() ->
				new FuncType(new ResultType(ImmutableList.of()), new ResultType(ImmutableList.of()));
			case ControlInstr.BlockType.OfIndex(var typeIdx) -> getFuncType(types.get(typeIdx.index()));
			case ControlInstr.BlockType.OfValType(var valType) ->
				new FuncType(new ResultType(ImmutableList.of()), new ResultType(ImmutableList.of(valType)));
		};
	}

	private ClassDesc getGlobalContainerType(GlobalType globalType, ClassDesc elementType) {
		return switch(globalType.mutability()) {
			case Const -> elementType;
			case Var -> ClassDesc.of(RUNTIME_PACKAGE, switch(globalType.type()) {
				case NumType numType -> switch(numType) {
					case I32 -> "GlobalI32";
					case I64 -> "GlobalI64";
					case F32 -> "GlobalF32";
					case F64 -> "GlobalF64";
				};
				default -> "GlobalRef";
			});
		};
	}

	private void loadLimits(CodeBuilder cb, Limits limits) {
		cb.loadConstant(limits.min());

		var maxSize = limits.max();
		if(maxSize == null) {
			cb.aconst_null();
		}
		else {
			cb.loadConstant(maxSize);
			cb.invokestatic(CD_Long, "valueOf", MethodTypeDesc.of(CD_Long, CD_long));
		}
	}

	private NumType addrValType(AddrType addrType) {
		return switch(addrType) {
			case I32 -> NumType.I32;
			case I64 -> NumType.I64;
		};
	}

	private ClassDesc addrDesc(AddrType addrType) {
		return switch(addrType) {
			case I32 -> CD_int;
			case I64 -> CD_long;
		};
	}

	private ClassDesc intSizeDesc(NumericInstr.NumSize numSize) {
		return switch(numSize) {
			case _32 -> CD_int;
			case _64 -> CD_long;
		};
	}

	private ClassDesc floatSizeDesc(NumericInstr.NumSize numSize) {
		return switch(numSize) {
			case _32 -> CD_float;
			case _64 -> CD_double;
		};
	}

	private record ImportModuleInfo(String module, String fieldName, WasmModuleRealization realization) {}

	private record FunctionInfo(
		String name,
		ClassDesc methodsClassName,
		MethodTypeDesc invokeMethodType,
		MethodTypeDesc lambdaMethodType,
		MethodTypeRealization methodType,
		FuncType funcType,
		DefType defType
	) {}

	private record TableInfo(String fieldName, ClassDesc elementType, TableType tableType) {}

	private record GlobalInfo(
		String fieldName,
		ClassDesc containerType,
		ClassDesc elementType,
		GlobalType globalType
	) {}

	private record MemInfo(String fieldName, MemType memType) {
		ClassDesc memoryClass() {
			return ClassDesc.of(RUNTIME_PACKAGE, memType.addrType() == AddrType.I32 ? "WasmMemory32" : "WasmMemory64");
		}
	}

	private record ElemInfo(String fieldName, ClassDesc fieldType, ClassDesc elementType, Elem elem) {}

	private record TagInfo(TagRealization realization, FuncType funcType, DefType defType) {}

	private record LocalInfo(int slotIndex, ClassDesc type) {}

}
