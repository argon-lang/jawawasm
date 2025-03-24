package dev.argon.jawawasm.engine.compiler;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import dev.argon.jawawasm.engine.ModuleResolver;
import dev.argon.jawawasm.engine.internal.SubtypingBase;
import dev.argon.jawawasm.engine.internal.TypeClosure;
import dev.argon.jawawasm.engine.internal.TypeRoll;
import dev.argon.jawawasm.engine.internal.TypeUnroll;
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
import java.lang.classfile.instruction.SwitchCase;
import java.lang.constant.*;
import java.util.*;
import java.util.function.Consumer;

import static dev.argon.jawawasm.engine.compiler.Constants.*;
import static dev.argon.jawawasm.engine.compiler.NameMangling.escapeName;
import static dev.argon.jawawasm.engine.compiler.WasmClassGeneratorUtils.*;
import static java.lang.constant.ConstantDescs.*;

class ModuleClassGenerator extends WasmClassGenerator {
	ModuleClassGenerator(ModuleCompiler compiler, Module module, String className, ModuleResolver<WasmModuleRealization> resolver) {
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

	private final List<DefType> types = new ArrayList<>();
	private final List<String> importModules = new ArrayList<>();
	private final Map<String, ImportModuleInfo> importModuleInfos = new HashMap<>();
	private final List<FunctionInfo> funcs = new ArrayList<>();
	private final List<TableInfo> tables = new ArrayList<>();
	private final List<GlobalInfo> globals = new ArrayList<>();
	private final List<MemInfo> mems = new ArrayList<>();
	private final List<TagInfo> tags = new ArrayList<>();
	private final List<@Nullable ElemInfo> elems = new ArrayList<>();
	private final List<WasmExportRealization> exports = new ArrayList<>();

	private byte @Nullable[] cachedBytecode = null;

	@Override
	public ClassDesc className() {
		return className;
	}

	WasmModuleRealization realization() {
		return new WasmModuleRealization(
			className,
			ImmutableList.copyOf(exports)
		);
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

		List<Consumer<CodeBuilder>> constructorInits = new ArrayList<>();
		List<ClassDesc> constructorParams = new ArrayList<>();

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
				} catch(ModuleResolutionException e) {
					throw new ModuleLinkException(e);
				}
				importModuleInfo = new ImportModuleInfo(imp.module(), "import" + importIndex, importRealization);
				importModuleInfos.put(imp.module(), importModuleInfo);

				clb.withField(importModuleInfo.fieldName, importRealization.classDesc(), ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);

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
				.filter(e -> e.exportName().equals(imp.name()))
				.findFirst()
				.orElse(null);

			if(exportRealization == null) {
				throw new ModuleLinkException("unknown import " + imp.name());
			}

			switch(imp.desc()) {
				case ImportDesc.Func func -> {
					if(
						!(exportRealization instanceof WasmExportRealization.OfInstanceMethod exportRealMethod) ||
							!(exportRealMethod.externalType() instanceof DefType importDefType)
					) {
						throw new ModuleLinkException("incompatible import type");
					}

					localName = "func" + funcs.size();
					var defType = closure.resolveDefType(types.get(func.type().index()));

					if(!subtyping.isSubtypeDefType(importDefType, defType)) {
						throw new ModuleLinkException("incompatible import type: import " + imp.name() + " from " + imp.module() + " Expected " + defType + ", Actual " + importDefType);
					}

					var type = compiler.getMethodType(defType);
					funcs.add(new FunctionInfo(localName, type, getFuncType(defType), defType));
					generateFunctionImport(clb, importModuleInfo, func, localName, type, exportRealMethod);
				}
				case ImportDesc.Table table -> {
					if(
						!(exportRealization instanceof WasmExportRealization.OfInstanceMethod exportRealMethod) ||
							!(exportRealMethod.externalType() instanceof TableType importTableType)
					) {
						throw new ModuleLinkException("incompatible import type");
					}

					localName = "table" + tables.size();
					var tableType = closure.resolveTableType(table.type());
					var elementType = compiler.getValType(tableType.elementType()).type();

					if(
						!subtyping.isSubtypeTableIgnoreMin(importTableType, closure.resolveTableType(table.type())) ||
							tableType.limits().min() < 0 || tableType.limits().min() > Integer.MAX_VALUE
					) {
						throw new ModuleLinkException("incompatible import type");
					}

					tables.add(new TableInfo(localName, elementType, tableType));
					clb.withField(localName, wasmTable, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);

					constructorInits.add(cb -> {
						cb.aload(0);
						cb.aload(0);
						cb.getfield(className, importModuleInfo.fieldName, importModuleInfo.realization().classDesc());
						cb.invokevirtual(importModuleInfo.realization().classDesc(), exportRealMethod.methodName(), exportRealMethod.methodType());
						cb.putfield(className, localName, wasmTable);


						cb.aload(0);
						cb.getfield(className, localName, wasmTable);
						cb.loadConstant((int)tableType.limits().min());
						cb.invokevirtual(wasmTable, "ensureMinimumSize", MethodTypeDesc.of(CD_void, CD_int));
					});
				}
				case ImportDesc.Global global -> {
					if(
						!(exportRealization instanceof WasmExportRealization.OfInstanceMethod exportRealMethod) ||
							!(exportRealMethod.externalType() instanceof GlobalType importGlobalType)
					) {
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

					clb.withField(localName, containerType, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);

					constructorInits.add(cb -> {
						cb.aload(0);
						cb.aload(0);
						cb.getfield(className, importModuleInfo.fieldName, importModuleInfo.realization().classDesc());
						cb.invokevirtual(importModuleInfo.realization().classDesc(), exportRealMethod.methodName(), exportRealMethod.methodType());
						cb.putfield(className, localName, containerType);
					});
				}
				case ImportDesc.Mem mem -> {
					if(
						!(exportRealization instanceof WasmExportRealization.OfInstanceMethod exportRealMethod) ||
							!(exportRealMethod.externalType() instanceof MemType importMemType)
					) {
						throw new ModuleLinkException("incompatible import type");
					}

					if(
						!subtyping.isSubtypeMemoryIgnoreMin(importMemType, mem.type()) ||
							mem.type().limits().min() < 0 || mem.type().limits().min() > Integer.MAX_VALUE
					) {
						throw new ModuleLinkException("incompatible import type");
					}

					localName = "mem" + mems.size();
					mems.add(new MemInfo(localName, mem.type()));


					clb.withField(localName, wasmMemory, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);

					constructorInits.add(cb -> {
						cb.aload(0);
						cb.aload(0);
						cb.getfield(className, importModuleInfo.fieldName, importModuleInfo.realization().classDesc());
						cb.invokevirtual(importModuleInfo.realization().classDesc(), exportRealMethod.methodName(), exportRealMethod.methodType());
						cb.putfield(className, localName, wasmMemory);

						cb.aload(0);
						cb.getfield(className, localName, wasmMemory);
						cb.loadConstant((int)mem.type().limits().min());
						cb.invokevirtual(wasmMemory, "ensureMinimumSize", MethodTypeDesc.of(CD_void, CD_int));
					});
				}
				case ImportDesc.Tag tag -> {
					if(!(exportRealization instanceof WasmExportRealization.OfInnerClass exportRealClass)) {
						throw new ModuleLinkException("incompatible import type");
					}

					var defType = closure.resolveDefType(types.get(tag.type().funcType().index()));
					var funcType = getFuncType(defType);

					if(
						!subtyping.isSubtypeDefType(exportRealClass.tagDefType(), defType)
					) {
						throw new ModuleLinkException("incompatible import type");
					}

					tags.add(new TagInfo(
						new TagRealization(
							exportRealClass.classDesc(),
							exportRealClass.innerClass(),
							exportRealClass.constructorType()
						),
						funcType,
						defType
					));
				}
			}
		}

		List<Runnable> functionCodegens = new ArrayList<>();

		for(var func : module.funcs()) {
			var name = "func" + funcs.size();
			var defType = closure.resolveDefType(types.get(func.type().index()));
			var type = compiler.getMethodType(defType);
			funcs.add(new FunctionInfo(name, type, getFuncType(defType), defType));
			functionCodegens.add(() -> generateFunction(clb, name, type, func));
		}

		for(var global : module.globals()) {
			var name = "global" + globals.size();
			var globalType = closure.resolveGlobalType(global.type());
			ClassDesc elementType = compiler.getValType(globalType.type()).type();
			ClassDesc containerType = getGlobalContainerType(globalType, elementType);

			clb.withField(name, containerType, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);

			constructorInits.add(cb -> {
				cb.aload(0);

				if(globalType.mutability() == Mut.Var) {
					cb.new_(containerType);
					cb.dup();
				}

				var bytecodeGen = new BytecodeGenerator(cb, new LocalInfo[] {}, new ResultType(ImmutableList.of(globalType.type())), 0);
				bytecodeGen.generateInstructionBlock(global.init());

				if(globalType.mutability() == Mut.Var) {
					cb.invokespecial(containerType, "<init>", MethodTypeDesc.of(CD_void, elementType.isPrimitive() ? elementType : CD_Object));
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
			clb.withField(name, wasmTable, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);

			constructorInits.add(cb -> {
				cb.aload(0);
				loadLimits(cb, tableType.limits());

				var bytecodeGen = new BytecodeGenerator(cb, new LocalInfo[] {}, new ResultType(ImmutableList.of(table.type().elementType())), 0);
				bytecodeGen.generateInstructionBlock(table.init());

				cb.invokestatic(wasmTable, "create", MethodTypeDesc.of(wasmTable, CD_long, CD_Long, CD_Object));
				cb.putfield(className, name, wasmTable);
			});
		}

		for(var memory : module.mems()) {
			var name = "mem" + mems.size();
			mems.add(new MemInfo(name, memory.type()));

			clb.withField(name, wasmMemory, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);
			constructorInits.add(cb -> {
				var memAlloc = ClassDesc.of(RUNTIME_PACKAGE, "MemoryAllocator");
				var addrType = ClassDesc.of(RUNTIME_PACKAGE, "AddrType");

				cb.aload(0);


				cb.aload(1);
				cb.invokeinterface(
					ClassDesc.of(RUNTIME_PACKAGE, "RuntimeContext"),
					"allocator",
					MethodTypeDesc.of(memAlloc)
				);

				cb.getstatic(addrType, memory.type().addrType().name(), addrType);

				loadLimits(cb, memory.type().limits());

				cb.invokestatic(
					wasmMemory,
					"create",
					MethodTypeDesc.of(
						wasmMemory,
						memAlloc,
						addrType,
						CD_long,
						CD_Long
					)
				);

				cb.putfield(className, name, wasmMemory);
			});
		}

		for(var elem : module.elems()) {
			var fieldName = "elem" + elems.size();

			var elementType = compiler.getValType(closure.resolveValType(elem.type())).type();
			var arrayType = elementType.arrayType();

			elems.add(new ElemInfo(fieldName, arrayType, elementType, elem));

			clb.withField(fieldName, arrayType, ClassFile.ACC_PRIVATE);
			constructorInits.add(cb -> {
				var elemSize = elem.mode() instanceof ElemMode.Declarative ? 0 : elem.init().size();

				cb.aload(0);
				cb.loadConstant(elemSize);
				cb.anewarray(elementType);
				cb.putfield(className, fieldName, arrayType);

				for(int i = 0; i < elemSize; ++i) {
					cb.aload(0);
					cb.getfield(className, fieldName, arrayType);
					cb.loadConstant(i);

					var bytecodeGen = new BytecodeGenerator(cb, new LocalInfo[] {}, new ResultType(ImmutableList.of(elem.type())), 0);
					bytecodeGen.generateInstructionBlock(elem.init().get(i));

					cb.aastore();
				}

				if(elem.mode() instanceof ElemMode.Active(var tableIdx, var offset)) {
					var table = tables.get(tableIdx.index());
					var at = addrDesc(table.tableType.addrType());

					var bytecodeGen = new BytecodeGenerator(cb, new LocalInfo[] {}, new ResultType(ImmutableList.of(addrValType(table.tableType.addrType()))), 0);
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
						MethodTypeDesc.of(
							CD_void,
							at,
							CD_int,
							CD_int,
							CD_Object.arrayType(),
							wasmTable
						)
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
			clb.withField(fieldName, CD_byte.arrayType(), ClassFile.ACC_PRIVATE);
			constructorInits.add(cb -> {
				var inputStream = ClassDesc.of("java.io.InputStream");

				cb.aload(0);
				cb.loadConstant(className);
				cb.loadConstant(simpleClassName + ".data" + index + ".dat");
				cb.invokevirtual(CD_Class, "getResourceAsStream", MethodTypeDesc.of(inputStream, CD_String));
				cb.invokestatic(ClassDesc.of(RUNTIME_PACKAGE, "Util"), "readData", MethodTypeDesc.of(CD_byte.arrayType(), inputStream));
				cb.putfield(className, fieldName, CD_byte.arrayType());

				if(data.mode() instanceof DataMode.Active(var memIdx, var offset)) {
					var mem = mems.get(memIdx.index());
					var at = addrDesc(mem.memType().addrType());

					var bytecodeGen = new BytecodeGenerator(cb, new LocalInfo[]{}, new ResultType(ImmutableList.of(addrValType(mem.memType().addrType()))), 0);
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
						MethodTypeDesc.of(
							CD_void,
							at,
							CD_int,
							CD_int,
							CD_byte.arrayType(),
							wasmMemory
						)
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

		for(var funcCodegen : functionCodegens) {
			funcCodegen.run();
		}

		for(var export : module.exports()) {
			switch(export.desc()) {
				case ExportDesc.Func func -> {
					var funcInfo = funcs.get(func.func().index());
					var methodName = escapeName(export.name());
					var methodType = funcInfo.type();
					clb.withMethodBody(
						methodName,
						methodType,
						ClassFile.ACC_PUBLIC,
						cb -> {
							cb.aload(0);

							int slotIndex = 1;
							for(var t : methodType.parameterList()) {
								cb.loadLocal(typeKind(t), slotIndex);
								slotIndex += slotSize(t);
							}

							cb.invokevirtual(className, funcInfo.name(), methodType);
							cb.areturn();
						}
					);
					exports.add(new WasmExportRealization.OfInstanceMethod(export.name(), methodName, methodType, funcInfo.defType));
				}
				case ExportDesc.Global global -> {
					var globalInfo = globals.get(global.global().index());
					var methodName = escapeName(export.name());
					var globalType = globalInfo.containerType();
					var methodType = MethodTypeDesc.of(globalType);
					clb.withMethodBody(
						methodName,
						methodType,
						ClassFile.ACC_PUBLIC,
						cb -> {
							cb.aload(0);
							cb.getfield(className, globalInfo.fieldName, globalType);
							if(globalType.isPrimitive()) {
								cb.return_(typeKind(globalType));
							}
							else {
								cb.areturn();
							}
						}
					);
					exports.add(new WasmExportRealization.OfInstanceMethod(export.name(), methodName, methodType, globalInfo.globalType()));
				}
				case ExportDesc.Mem mem -> {
					var memInfo = mems.get(mem.mem().index());
					var methodName = escapeName(export.name());
					var methodType = MethodTypeDesc.of(wasmMemory);
					clb.withMethodBody(
						methodName,
						methodType,
						ClassFile.ACC_PUBLIC,
						cb -> {
							cb.aload(0);
							cb.getfield(className, memInfo.fieldName, wasmMemory);
							cb.areturn();
						}
					);
					exports.add(new WasmExportRealization.OfInstanceMethod(export.name(), methodName, methodType, memInfo.memType()));
				}
				case ExportDesc.Table table -> {
					var tableInfo = tables.get(table.table().index());
					var methodName = escapeName(export.name());
					var methodType = MethodTypeDesc.of(wasmTable);
					clb.withMethodBody(
						methodName,
						methodType,
						ClassFile.ACC_PUBLIC,
						cb -> {
							cb.aload(0);
							cb.getfield(className, tableInfo.fieldName, wasmTable);
							cb.areturn();
						}
					);
					exports.add(new WasmExportRealization.OfInstanceMethod(export.name(), methodName, methodType, tableInfo.tableType()));
				}
				case ExportDesc.Tag tag -> {
					var tagInfo = tags.get(tag.tag().index());

					exports.add(new WasmExportRealization.OfInnerClass(
						export.name(),
						tagInfo.realization().classDesc(),
						tagInfo.realization().innerClassInfo(),
						tagInfo.realization().constructorType(),
						tagInfo.funcType,
						tagInfo.defType
					));
				}
			}
		}
	}


	private void generateTags() {
		for(var tag : module.tags()) {
			var defType = closure.resolveDefType(types.get(tag.type().funcType().index()));
			var funcType = getFuncType(defType);
			var tagGen = new TagExceptionClassGenerator(compiler, className, "Tag" + tags.size(), funcType);
			tags.add(new TagInfo(tagGen.realization(), funcType, defType));
			compiler.enqueueGenerator(tagGen);
		}
	}


	private void generateConstructor(ClassBuilder clb, List<Consumer<CodeBuilder>> constructorInits, List<ClassDesc> constructorParams) {
		var ctorType = MethodTypeDesc.of(CD_void, constructorParams);

		clb.withMethodBody("<init>", ctorType, ClassFile.ACC_PUBLIC, cb -> {
			cb.aload(0).invokespecial(wasmModuleClass, "<init>", MethodTypeDesc.ofDescriptor("()V"));

			for(var ctorInit : constructorInits) {
				ctorInit.accept(cb);
			}

			var startSection = module.start();
			if(startSection != null) {
				var func = funcs.get(startSection.func().index());
				var resultType = func.type.returnType();
				var endResultType = resultType.nested("EndResult");

				cb.aload(0);
				cb.invokevirtual(className, func.name, func.type);
				cb.invokestatic(resultType, "get", MethodTypeDesc.of(endResultType, resultType), true);
			}

			cb.return_();
		});
	}

	private void generateFunction(ClassBuilder clb, String name, MethodTypeDesc type, Func func) {
		System.err.println("generateFunction " + name);
		clb.withMethodBody(name, type, ClassFile.ACC_PRIVATE, cb -> {
			var funcType = closure.resolveDefType(types.get(func.type().index()));

			var returnType = getFuncType(funcType).results();

			int paramCount = type.parameterCount();
			LocalInfo[] locals = new LocalInfo[paramCount + func.locals().size()];
			int slotOffset = 1;
			for(int i = 0; i < paramCount; ++i) {
				var paramType = type.parameterType(i);
				locals[i] = new LocalInfo(slotOffset, paramType);
				slotOffset += slotSize(paramType);
			}

			for(int i = 0; i < func.locals().size(); ++i) {
				var localType = compiler.getValType(closure.resolveValType(func.locals().get(i)));
				locals[paramCount + i] = new LocalInfo(slotOffset, localType.type());

				switch(typeKind(localType.type())) {
					case BOOLEAN, BYTE, CHAR, SHORT, INT -> cb.iconst_0().istore(slotOffset);
					case LONG -> cb.lconst_0().lstore(slotOffset);
					case FLOAT -> cb.fconst_0().fstore(slotOffset);
					case DOUBLE -> cb.dconst_0().dstore(slotOffset);
					case REFERENCE -> cb.aconst_null().astore(slotOffset);
					case VOID -> throw new RuntimeException("Unexpected VOID elementType");
				}

				slotOffset += slotSize(localType.type());
			}

			var bytecodeGen = new BytecodeGenerator(cb, locals, returnType, slotOffset);
			bytecodeGen.generateFunctionBody(func.body());
		});

		generateStaticThunk(clb, name, type);
	}

	private void generateStaticThunk(ClassBuilder clb, String name, MethodTypeDesc type) {
		// Generate static method that is easier to call.
		var staticType = getStaticThunkType(type);
		clb.withMethodBody("static_" + name, staticType, ClassFile.ACC_STATIC | ClassFile.ACC_PRIVATE, cb -> {

			// Find the offset of this.
			int slotOffset = 0;
			for(int i = 0; i < type.parameterCount(); ++i) {
				var paramType = type.parameterType(i);
				slotOffset += slotSize(paramType);
			}

			cb.aload(slotOffset);

			slotOffset = 0;
			for(int i = 0; i < type.parameterCount(); ++i) {
				var paramType = type.parameterType(i);
				cb.loadLocal(typeKind(paramType), slotOffset);
				slotOffset += slotSize(paramType);
			}

			cb.invokevirtual(className, name, type);
			cb.areturn();
		});
	}

	private MethodTypeDesc getStaticThunkType(MethodTypeDesc type) {
		var staticTypeArgs = new ArrayList<>(type.parameterList());
		staticTypeArgs.add(className);
		var staticType = MethodTypeDesc.of(type.returnType(), staticTypeArgs);
		return staticType;
	}

	private class BytecodeGenerator {
		public BytecodeGenerator(CodeBuilder cb, LocalInfo[] locals, ResultType returnType, int tempVarSlot) {
			this.cb = cb;
			this.locals = locals;
			this.returnType = returnType;
			this.tempVarSlot = tempVarSlot;
		}


		private final CodeBuilder cb;
		private final LocalInfo[] locals;
		private final ResultType returnType;
		private int tempVarSlot;
		private final List<TypeKind> stackTypes = new ArrayList<>();
		private final List<LabelInfo> labels = new ArrayList<>();
		private final Set<Label> jumpedLabels = new HashSet<>();
		private boolean isUnreachable = false;

		public void generateFunctionBody(Expr body) {
			var returnLabel = cb.newLabel();
			labels.add(new LabelInfo(returnLabel, returnType));
			generateInstructionBlock(body);

			if(!isUnreachable || jumpedLabels.contains(returnLabel)) {
				cb.labelBinding(returnLabel);
				generateReturn();
			}
		}

		public void generateInstructionBlock(Expr body) {
			for(var insn : body.body()) {
				System.err.println("Instruction: " + insn);
				System.err.println("Stack " + stackTypes);

				generateInstruction(insn);

				if(isUnreachable) {
					break;
				}
			}
		}


		private void generateInstruction(Instr insn) {
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

		private void generateControlInstr(ControlInstr instr) {
			switch(instr) {
				case ControlInstr.Nop() -> cb.nop();
				case ControlInstr.Unreachable() -> {
					var trap = ClassDesc.of(RUNTIME_PACKAGE, "UnreachableTrap");
					cb.new_(trap);
					cb.dup();
					cb.invokespecial(trap, "<init>", MethodTypeDesc.ofDescriptor("()V"));
					cb.athrow();
					isUnreachable = true;
					stackTypes.clear();
				}
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
					if(!isUnreachable) cb.goto_(endLabel);
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
				case ControlInstr.Throw(var tagIdx) -> {
					var tagInfo = tags.get(tagIdx.index());

					saveStackTempRes(tagInfo.funcType.args());
					cb.new_(tagInfo.realization.classDesc());
					cb.dup();
					restoreStackTempRes(tagInfo.funcType.args());
					cb.invokespecial(
						tagInfo.realization.classDesc(),
						"<init>",
						tagInfo.realization.constructorType()
					);
					cb.athrow();

					stackTypes.clear();
					isUnreachable = true;
				}
				case ControlInstr.Throw_Ref() -> {
					cb.athrow();
					stackTypes.clear();
					isUnreachable = true;
				}
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

						cb.istore(indexVarSlot);
						fixJumpStack(fallbackLabel.labelType);
						cb.iload(indexVarSlot);

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

				case ControlInstr.Return() -> generateReturn();

				case ControlInstr.Call(var funcIdx) -> {
					var funcInfo = funcs.get(funcIdx.index());

					var resultType = compiler.getResultType(funcInfo.funcType.results());
					var endResultType = resultType.nested("EndResult");

					for(int i = 0; i < funcInfo.type.parameterCount(); ++i) {
						stackTypes.removeLast();
					}

					cb.aload(0);
					cb.invokestatic(className, "static_" + funcInfo.name(), getStaticThunkType(funcInfo.type()));
					cb.invokestatic(resultType, "get", MethodTypeDesc.of(endResultType, resultType), true);
					unpackResultType(funcInfo.funcType.results(), endResultType);
				}
				case ControlInstr.Call_Ref(var funcTypeIdx) -> {
					var defType = closure.resolveDefType(types.get(funcTypeIdx.index()));
					var realizedType = (FuncTypeRealization)compiler.getDefType(defType);
					var realizedMethodType = realizedType.methodType().get();
					var funcType = getFuncType(defType);


					var functionObjSlot = tempVarSlot;
					++tempVarSlot;

					cb.astore(functionObjSlot);
					stackTypes.removeLast();

					saveStackTempDesc(realizedMethodType.parameterList());

					cb.aload(functionObjSlot);
					stackTypes.add(TypeKind.REFERENCE);

					restoreStackTempDesc(realizedMethodType.parameterList());

					for(var _ : realizedMethodType.parameterList()) {
						stackTypes.removeLast();
					}

					stackTypes.removeLast();


					var resultType = realizedMethodType.returnType();
					var endResultType = resultType.nested("EndResult");

					cb.invokeinterface(realizedType.classDesc(), realizedType.methodName(), realizedMethodType);
					cb.invokestatic(resultType, "get", MethodTypeDesc.of(endResultType, resultType), true);
					unpackResultType(funcType.results(), endResultType);

					tempVarSlot = functionObjSlot;
				}
				case ControlInstr.Call_Indirect(var tableIdx, var funcTypeIdx) -> {
					var tableInfo = tables.get(tableIdx.index());
					cb.aload(0);
					cb.getfield(className, tableInfo.fieldName, wasmTable);
					cb.invokestatic(wasmTable, "table_get", MethodTypeDesc.of(CD_Object, addrDesc(tableInfo.tableType.addrType()), wasmTable));

					var defType = types.get(funcTypeIdx.index());
					var realizedType = compiler.getDefType(defType);
					cb.checkcast(realizedType.classDesc());

					stackTypes.removeLast();
					stackTypes.add(TypeKind.REFERENCE);

					generateControlInstr(new ControlInstr.Call_Ref(funcTypeIdx));
				}
				case ControlInstr.Return_Call(var funcIdx) -> {
					var funcInfo = funcs.get(funcIdx.index());

					var resultType = compiler.getResultType(funcInfo.funcType.results());
					var stepResultType = resultType.nested("Step");

					var invokeDynamicSig = funcInfo.type
						.insertParameterTypes(funcInfo.type.parameterCount(), className)
						.changeReturnType(stepResultType);

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
							className,
							"static_" + funcInfo.name,
							getStaticThunkType(funcInfo.type)
						),
						stepSig
					);

					cb.aload(0);
					cb.invokedynamic(callSite);
					cb.areturn();
					stackTypes.clear();
					isUnreachable = true;
				}
				case ControlInstr.Return_Call_Ref(var funcTypeIdx) -> {
					var defType = closure.resolveDefType(types.get(funcTypeIdx.index()));
					var realizedType = (FuncTypeRealization)compiler.getDefType(defType);
					var realizedMethodType = realizedType.methodType().get();


					cb.dup();
					cb.invokestatic(ClassDesc.of("java.util.Objects"), "requireNonNull", MethodTypeDesc.of(CD_Object, CD_Object));
					cb.pop();


					var functionObjSlot = tempVarSlot;
					++tempVarSlot;

					cb.astore(functionObjSlot);
					stackTypes.removeLast();

					saveStackTempDesc(realizedMethodType.parameterList());

					cb.aload(functionObjSlot);
					stackTypes.add(TypeKind.REFERENCE);

					restoreStackTempDesc(realizedMethodType.parameterList());


					var resultType = realizedMethodType.returnType();
					var stepResultType = resultType.nested("Step");

					var invokeDynamicSig = realizedMethodType
						.insertParameterTypes(0, realizedType.classDesc())
						.changeReturnType(stepResultType);

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
							realizedMethodType
						),
						stepSig
					);

					cb.invokedynamic(callSite);
					cb.areturn();

					tempVarSlot = functionObjSlot;

					stackTypes.clear();
					isUnreachable = true;
				}
				case ControlInstr.Return_Call_Indirect(var tableIdx, var funcTypeIdx) -> {
					var tableInfo = tables.get(tableIdx.index());
					cb.aload(0);
					cb.getfield(className, tableInfo.fieldName, wasmTable);
					cb.invokestatic(wasmTable, "table_get", MethodTypeDesc.of(CD_Object, addrDesc(tableInfo.tableType.addrType()), wasmTable));

					var defType = types.get(funcTypeIdx.index());
					var realizedType = compiler.getDefType(defType);
					cb.checkcast(realizedType.classDesc());

					stackTypes.removeLast();
					stackTypes.add(TypeKind.REFERENCE);

					generateControlInstr(new ControlInstr.Return_Call_Ref(funcTypeIdx));
				}

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
					if(!isUnreachable) cb.goto_(endLabel);

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
									cb.astore(tempVarSlot);

									for(int i = 0; i < types.size(); ++i) {
										cb.aload(tempVarSlot);
										cb.getfield(tagInfo.realization.classDesc(), "item" + i, compiler.getValType(types.get(i)).type());
									}
								}

								var targetLabel = getLabel(labelIdx).label;
								cb.goto_(targetLabel);
								jumpedLabels.add(targetLabel);

								cb.exceptionCatch(tryStartLabel, tryEndLabel, catchLabel, tagInfo.realization.classDesc());
							}
							case ControlInstr.CatchTagRef(var tagIdx, var labelIdx) -> {
								var tagInfo = tags.get(tagIdx.index());

								var catchLabel = cb.newLabel();
								cb.labelBinding(catchLabel);

								var types = tagInfo.funcType.args().types();
								if(!types.isEmpty()) {
									cb.astore(tempVarSlot);

									for(int i = 0; i < types.size(); ++i) {
										cb.aload(tempVarSlot);
										cb.getfield(tagInfo.realization.classDesc(), "item" + i, compiler.getValType(types.get(i)).type());
									}

									cb.aload(tempVarSlot);
								}

								var targetLabel = getLabel(labelIdx).label;
								cb.goto_(targetLabel);
								jumpedLabels.add(targetLabel);

								cb.exceptionCatch(tryStartLabel, tryEndLabel, catchLabel, tagInfo.realization.classDesc());
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

//				case ControlInstr.Br_OnCast brOnCast -> {
//				}
//				case ControlInstr.Br_OnCastFail brOnCastFail -> {
//				}

				default -> throw new RuntimeException("Not implemented: " + instr);
			}
		}

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
						case _32 -> {}
						case _64 -> {
							cb.i2l();
							stackTypes.removeLast();
							stackTypes.add(TypeKind.LONG);
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
							stackTypes.add(TypeKind.LONG);
						}
					}
				}
				case MemoryInstr.Inn_Store8(var numSize, var memArg) -> {
					switch(numSize) {
						case _32 -> {}
						case _64 -> {
							cb.l2i();
							stackTypes.removeLast();
							stackTypes.add(TypeKind.INT);
						}
					}
					doStore(CD_byte, memArg);
				}
				case MemoryInstr.Inn_Load16_S(var numSize, var memArg) -> {
					doLoad(CD_short, memArg);
					switch(numSize) {
						case _32 -> {}
						case _64 -> {
							cb.i2l();
							stackTypes.removeLast();
							stackTypes.add(TypeKind.LONG);
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
							stackTypes.add(TypeKind.LONG);
						}
					}
				}
				case MemoryInstr.Inn_Store16(var numSize, var memArg) -> {
					switch(numSize) {
						case _32 -> {}
						case _64 -> {
							cb.l2i();
							stackTypes.removeLast();
							stackTypes.add(TypeKind.INT);
						}
					}
					doStore(CD_short, memArg);
				}

				case MemoryInstr.I64_Load32_S(var memArg) -> {
					doLoad(CD_int, memArg);
					cb.i2l();
					stackTypes.removeLast();
					stackTypes.add(TypeKind.LONG);
				}
				case MemoryInstr.I64_Load32_U(var memArg) -> {
					doLoad(CD_int, memArg);
					cb.invokestatic(CD_Integer, "toUnsignedLong", MethodTypeDesc.ofDescriptor("(I)J"));
					stackTypes.removeLast();
					stackTypes.add(TypeKind.LONG);
				}
				case MemoryInstr.I64_Store32(var memArg) -> {
					stackTypes.removeLast();
					stackTypes.add(TypeKind.INT);
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

					loadMem(memIdx);

					cb.invokevirtual(wasmMemory, "pageSize", MethodTypeDesc.of(CD_long));

					switch(mem.memType.addrType()) {
						case I32 -> cb.l2i();
						case I64 -> {}
					}
				}
				case MemoryInstr.Memory_Grow(var memIdx) -> {
					var mem = mems.get(memIdx.index());
					var at = addrDesc(mem.memType.addrType());

					loadMem(memIdx);

					cb.invokestatic(wasmMemory, "grow", MethodTypeDesc.of(at, at, wasmMemory));
				}
				case MemoryInstr.Memory_Fill(var memIdx) -> {
					var mem = mems.get(memIdx.index());
					var at = addrDesc(mem.memType.addrType());
					cb.aload(0);
					cb.getfield(className, mem.fieldName, wasmMemory);
					cb.invokestatic(
						wasmMemory,
						"fill",
						MethodTypeDesc.of(
							CD_void,
							at,
							CD_byte,
							at,
							wasmMemory
						)
					);
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

					cb.aload(0);
					cb.getfield(className, dstMem.fieldName, wasmMemory);
					cb.aload(0);
					cb.getfield(className, srcMem.fieldName, wasmMemory);
					cb.invokestatic(
						wasmMemory,
						"copy",
						MethodTypeDesc.of(
							CD_void,
							dat,
							sat,
							minAt,
							wasmMemory,
							wasmMemory
						)
					);
					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.removeLast();
				}
				case MemoryInstr.Memory_Init(var memIdx, var dataIdx) -> {
					var mem = mems.get(memIdx.index());
					var at = addrDesc(mem.memType.addrType());

					cb.aload(0);
					cb.getfield(className, "data" + dataIdx.index(), CD_byte.arrayType());

					cb.aload(0);
					cb.getfield(className, mem.fieldName, wasmMemory);

					cb.invokestatic(
						wasmMemory,
						"copyFromArray",
						MethodTypeDesc.of(
							CD_void,
							at,
							CD_int,
							CD_int,
							CD_byte.arrayType(),
							wasmMemory
						)
					);
					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.removeLast();
				}
				case MemoryInstr.Data_Drop(var dataIdx) -> {
					cb.aload(0);
					cb.iconst_0();
					cb.newarray(TypeKind.BYTE);
					cb.putfield(className, "data" + dataIdx.index(), CD_byte.arrayType());
				}

//				case MemoryInstr.V128_Load v128Load -> {
//				}
//				case MemoryInstr.V128_Load16_Lane v128Load16Lane -> {
//				}
//				case MemoryInstr.V128_Load16_Splat v128Load16Splat -> {
//				}
//				case MemoryInstr.V128_Load16x4_S v128Load16x4S -> {
//				}
//				case MemoryInstr.V128_Load16x4_U v128Load16x4U -> {
//				}
//				case MemoryInstr.V128_Load32_Lane v128Load32Lane -> {
//				}
//				case MemoryInstr.V128_Load32_Splat v128Load32Splat -> {
//				}
//				case MemoryInstr.V128_Load32_Zero v128Load32Zero -> {
//				}
//				case MemoryInstr.V128_Load32x2_S v128Load32x2S -> {
//				}
//				case MemoryInstr.V128_Load32x2_U v128Load32x2U -> {
//				}
//				case MemoryInstr.V128_Load64_Lane v128Load64Lane -> {
//				}
//				case MemoryInstr.V128_Load64_Splat v128Load64Splat -> {
//				}
//				case MemoryInstr.V128_Load64_Zero v128Load64Zero -> {
//				}
//				case MemoryInstr.V128_Load8_Lane v128Load8Lane -> {
//				}
//				case MemoryInstr.V128_Load8_Splat v128Load8Splat -> {
//				}
//				case MemoryInstr.V128_Load8x8_S v128Load8x8S -> {
//				}
//				case MemoryInstr.V128_Load8x8_U v128Load8x8U -> {
//				}
//				case MemoryInstr.V128_Store v128Store -> {
//				}
//				case MemoryInstr.V128_Store16_Lane v128Store16Lane -> {
//				}
//				case MemoryInstr.V128_Store32_Lane v128Store32Lane -> {
//				}
//				case MemoryInstr.V128_Store64_Lane v128Store64Lane -> {
//				}
//				case MemoryInstr.V128_Store8_Lane v128Store8Lane -> {
//				}

				default -> throw new RuntimeException("Not implemented: " + instr);
			}
		}

		private void generateNumericInstr(NumericInstr instr) {
			switch(instr) {
				case NumericInstr.I32_Const(var value) -> {
					cb.loadConstant(value);
					stackTypes.add(TypeKind.INT);
				}
				case NumericInstr.I64_Const(var value) -> {
					if(value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
						cb.loadConstant((int)value);
						cb.i2l();
					}
					else {
						cb.loadConstant(value);
					}
					stackTypes.add(TypeKind.LONG);
				}
				case NumericInstr.F32_Const(var value) -> {
					if(Float.isNaN(value) && Float.floatToRawIntBits(value) != Float.floatToRawIntBits(Float.NaN)) {
						cb.loadConstant(Float.floatToRawIntBits(value));
						cb.invokestatic(CD_Float, "intBitsToFloat", MethodTypeDesc.of(CD_float, CD_int));
					}
					else {
						cb.loadConstant(value);
					}
					stackTypes.add(TypeKind.FLOAT);
				}
				case NumericInstr.F64_Const(var value) -> {
					if(Double.isNaN(value) && Double.doubleToRawLongBits(value) != Double.doubleToRawLongBits(Double.NaN)) {
						cb.loadConstant(Double.doubleToRawLongBits(value));
						cb.invokestatic(CD_Double, "longBitsToDouble", MethodTypeDesc.of(CD_double, CD_long));
					}
					else {
						cb.loadConstant(value);
					}
					stackTypes.add(TypeKind.DOUBLE);
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
				case NumericInstr.Fnn_FUnOp(var size, var op) -> {
					var descriptor = switch(size) {
						case _32 -> MethodTypeDesc.ofDescriptor("(F)F");
						case _64 -> MethodTypeDesc.ofDescriptor("(D)D");
					};

					var nameSuffix = switch(size) {
						case _32 -> "F32";
						case _64 -> "F64";
					};

					switch(op) {
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
								case _64 -> {}
							}
							cb.invokestatic(mathClass, "sqrt", MethodTypeDesc.ofDescriptor("(D)D"));
							switch(size) {
								case _32 -> cb.d2f();
								case _64 -> {}
							}
						}
						case CEIL -> cb.invokestatic(utilClass, "ceil" + nameSuffix, descriptor);
						case FLOOR -> cb.invokestatic(utilClass, "floor" + nameSuffix, descriptor);
						case TRUNC -> cb.invokestatic(utilClass, "trunc" + nameSuffix, descriptor);
						case NEAREST -> cb.invokestatic(utilClass, "nearest" + nameSuffix, descriptor);
					}
				}
				case NumericInstr.Inn_IBinOp(var size, var op) -> {
					var descriptor = switch(size) {
						case _32 -> MethodTypeDesc.ofDescriptor("(II)I");
						case _64 -> MethodTypeDesc.ofDescriptor("(JJ)J");
					};

					switch(op) {
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
								case _64 -> cb.l2i().invokestatic(CD_Long, "rotateLeft", MethodTypeDesc.ofDescriptor("(JI)J"));
							}
						}
						case ROTR -> {
							switch(size) {
								case _32 -> cb.invokestatic(CD_Integer, "rotateRight", descriptor);
								case _64 -> cb.l2i().invokestatic(CD_Long, "rotateRight", MethodTypeDesc.ofDescriptor("(JI)J"));
							}
						}
					}

					stackTypes.removeLast();
				}
				case NumericInstr.Fnn_FBinOp(var size, var op) -> {
					var descriptor = switch(size) {
						case _32 -> MethodTypeDesc.ofDescriptor("(FF)F");
						case _64 -> MethodTypeDesc.ofDescriptor("(DD)D");
					};

					switch(op) {
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
				case NumericInstr.Inn_ITestOp(var size, var op) -> {
					var descriptor = switch(size) {
						case _32 -> MethodTypeDesc.ofDescriptor("(I)Z");
						case _64 -> MethodTypeDesc.ofDescriptor("(J)Z");
					};

					switch(op) {
						case EQZ -> cb.invokestatic(utilClass, "equalsZero", descriptor);
					}

					stackTypes.removeLast();
					stackTypes.add(TypeKind.INT);
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
					stackTypes.add(TypeKind.INT);
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
					stackTypes.add(TypeKind.INT);
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
					stackTypes.add(TypeKind.INT);
				}
				case NumericInstr.I64_Extend_I32_S() -> {
					cb.i2l();
					stackTypes.removeLast();
					stackTypes.add(TypeKind.LONG);
				}
				case NumericInstr.I64_Extend_I32_U() -> {
					cb.invokestatic(CD_Integer, "toUnsignedLong", MethodTypeDesc.ofDescriptor("(I)J"));
					stackTypes.removeLast();
					stackTypes.add(TypeKind.INT);
				}
				case NumericInstr.Inn_Trunc_Fmm_S(var intSize, var floatSize) -> {
					stackTypes.removeLast();

					switch(floatSize) {
						case _32 -> cb.f2d();
						case _64 -> {}
					}

					switch(intSize) {
						case _32 -> cb.invokestatic(utilClass, "truncF64ToS32", MethodTypeDesc.ofDescriptor("(D)I"));
						case _64 -> cb.invokestatic(utilClass, "truncF64ToS64", MethodTypeDesc.ofDescriptor("(D)J"));
					}

					stackTypes.add(typeKind(intSizeDesc(intSize)));
				}
				case NumericInstr.Inn_Trunc_Fmm_U(var intSize, var floatSize) -> {
					stackTypes.removeLast();

					switch(floatSize) {
						case _32 -> cb.f2d();
						case _64 -> {}
					}

					switch(intSize) {
						case _32 -> cb.invokestatic(utilClass, "truncF64ToU32", MethodTypeDesc.ofDescriptor("(D)I"));
						case _64 -> cb.invokestatic(utilClass, "truncF64ToU64", MethodTypeDesc.ofDescriptor("(D)J"));
					}

					stackTypes.add(typeKind(intSizeDesc(intSize)));
				}
				case NumericInstr.Inn_Trunc_Sat_Fmm_S(var intSize, var floatSize) -> {
					stackTypes.removeLast();
					switch(floatSize) {
						case _32 -> {
							switch(intSize) {
								case _32 -> cb.f2i();
								case _64 -> cb.f2l();
							};
							stackTypes.add(TypeKind.FLOAT);
						}
						case _64 -> {
							switch(intSize) {
								case _32 -> cb.d2i();
								case _64 -> cb.d2l();
							};
							stackTypes.add(TypeKind.DOUBLE);
						}
					}
				}
				case NumericInstr.Inn_Trunc_Sat_Fmm_U(var intSize, var floatSize) -> {
					stackTypes.removeLast();

					switch(floatSize) {
						case _32 -> {
							switch(intSize) {
								case _32 -> cb.invokestatic(utilClass, "truncSatF32U32", MethodTypeDesc.ofDescriptor("(F)I"));
								case _64 -> cb.invokestatic(utilClass, "truncSatF32U64", MethodTypeDesc.ofDescriptor("(F)J"));
							}
							stackTypes.add(TypeKind.FLOAT);
						}
						case _64 -> {
							switch(intSize) {
								case _32 -> cb.invokestatic(utilClass, "truncSatF64U32", MethodTypeDesc.ofDescriptor("(D)I"));
								case _64 -> cb.invokestatic(utilClass, "truncSatF64U64", MethodTypeDesc.ofDescriptor("(D)J"));
							}
							stackTypes.add(TypeKind.DOUBLE);
						}
					}


					stackTypes.add(typeKind(intSizeDesc(intSize)));
				}
				case NumericInstr.F32_Demote_F64() -> {
					cb.d2f();
				}
				case NumericInstr.F64_Promote_F32() -> {
					cb.f2d();
				}
				case NumericInstr.Fnn_Convert_Imm_S(var floatSize, var intSize) -> {
					stackTypes.removeLast();
					switch(floatSize) {
						case _32 -> {
							switch(intSize) {
								case _32 -> cb.i2f();
								case _64 -> cb.l2f();
							};
							stackTypes.add(TypeKind.FLOAT);
						}
						case _64 -> {
							switch(intSize) {
								case _32 -> cb.i2d();
								case _64 -> cb.l2d();
							};
							stackTypes.add(TypeKind.DOUBLE);
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
							stackTypes.add(TypeKind.FLOAT);
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
							stackTypes.add(TypeKind.DOUBLE);
						}
					}
				}
				case NumericInstr.Fnn_Reinterpret_Inn(var numSize) -> {
					switch(numSize) {
						case _32 -> cb.invokestatic(CD_Float, "intBitsToFloat", MethodTypeDesc.of(CD_float, CD_int));
						case _64 -> cb.invokestatic(CD_Double, "longBitsToDouble", MethodTypeDesc.of(CD_double, CD_long));
					}
				}
				case NumericInstr.Inn_Reinterpret_Fnn(var numSize) -> {
					switch(numSize) {
						case _32 -> cb.invokestatic(CD_Float, "floatToRawIntBits", MethodTypeDesc.of(CD_int, CD_float));
						case _64 -> cb.invokestatic(CD_Double, "doubleToRawLongBits", MethodTypeDesc.of(CD_long, CD_double));
					}
				}
			}
		}

		private void generateParametricInstr(ParametricInstr instr) {
			switch(instr) {
				case ParametricInstr.Drop() -> {
					var t = stackTypes.getLast();
					stackTypes.removeLast();

					if(t.slotSize() == 2) {
						cb.pop2();
					}
					else {
						cb.pop();
					}
				}
				case ParametricInstr.Select _ -> {
					stackTypes.removeLast();
					var t = stackTypes.getLast();
					stackTypes.removeLast();

					var endLabel = cb.newLabel();
					var bottomLabel = cb.newLabel();

					cb.ifeq(bottomLabel);

					if(t.slotSize() == 2) {
						cb.pop2();
					}
					else {
						cb.pop();
					}

					cb.goto_(endLabel);

					cb.labelBinding(bottomLabel);

					if(t.slotSize() == 2) {
						cb.dup2_x2();
						cb.pop2();
						cb.pop2();
					}
					else {
						cb.swap();
						cb.pop();
					}

					cb.labelBinding(endLabel);
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

						realizedMethodType,
						MethodHandleDesc.ofMethod(
							DirectMethodHandleDesc.Kind.VIRTUAL,
							className,
							funcInfo.name,
							funcInfo.type
						),
						realizedMethodType
					);

					cb.aload(0);
					cb.invokedynamic(callSite);
					stackTypes.add(TypeKind.REFERENCE);
				}

				case ReferenceInstr.Ref_IsNull() -> {
					cb.invokestatic(
						ClassDesc.of("java.util.Objects"),
						"isNull",
						MethodTypeDesc.of(CD_boolean, CD_Object)
					);
					stackTypes.removeLast();
					stackTypes.add(TypeKind.INT);
				}

				case ReferenceInstr.Ref_Null _ -> {
					cb.aconst_null();
					stackTypes.add(TypeKind.REFERENCE);
				}

				case ReferenceInstr.Ref_AsNonNull() -> {
					cb.dup();
					cb.invokestatic(ClassDesc.of("java.util.Objects"), "requireNonNull", MethodTypeDesc.of(CD_Object, CD_Object));
					cb.pop();
				}


//				case ReferenceInstr.Any_Convert_Extern anyConvertExtern -> {
//				}
//				case ReferenceInstr.ArrayInstr arrayInstr -> {
//				}
//				case ReferenceInstr.Extern_Convert_Any externConvertAny -> {
//				}
//				case ReferenceInstr.I31_Get_S i31GetS -> {
//				}
//				case ReferenceInstr.I31_Get_U i31GetU -> {
//				}
//				case ReferenceInstr.Ref_Cast refCast -> {
//				}
//				case ReferenceInstr.Ref_Eq refEq -> {
//				}
//				case ReferenceInstr.Ref_I31 refI31 -> {
//				}
//				case ReferenceInstr.Ref_Test refTest -> {
//				}
//				case ReferenceInstr.StructInstr structInstr -> {
//				}

				default -> throw new RuntimeException("Not implemented: " + instr);
			}
		}

		private void generateTableInstr(TableInstr instr) {
			switch(instr) {
				case TableInstr.Table_Get(var tableIdx) -> {
					var tableInfo = tables.get(tableIdx.index());
					cb.aload(0);
					cb.getfield(className, tableInfo.fieldName, wasmTable);
					cb.invokestatic(wasmTable, "table_get", MethodTypeDesc.of(CD_Object, addrDesc(tableInfo.tableType.addrType()), wasmTable));
					if(!tableInfo.elementType.equals(CD_Object)) {
						cb.checkcast(tableInfo.elementType);
					}
					stackTypes.removeLast();
					stackTypes.add(TypeKind.REFERENCE);
				}
				case TableInstr.Table_Set(var tableIdx) -> {
					var tableInfo = tables.get(tableIdx.index());
					cb.aload(0);
					cb.getfield(className, tableInfo.fieldName, wasmTable);
					cb.invokestatic(wasmTable, "table_set", MethodTypeDesc.of(CD_void, addrDesc(tableInfo.tableType.addrType()), CD_Object, wasmTable));
					stackTypes.removeLast();
					stackTypes.removeLast();
				}
				case TableInstr.Table_Size(var tableIdx) -> {
					var tableInfo = tables.get(tableIdx.index());
					cb.aload(0);
					cb.getfield(className, tableInfo.fieldName, wasmTable);
					cb.invokevirtual(wasmTable, "size", MethodTypeDesc.of(CD_int));
					switch(tableInfo.tableType.addrType()) {
						case I32 -> {}
						case I64 -> cb.i2l();
					}
				}
				case TableInstr.Table_Grow(var tableIdx) -> {
					var tableInfo = tables.get(tableIdx.index());
					var at = addrDesc(tableInfo.tableType.addrType());
					cb.aload(0);
					cb.getfield(className, tableInfo.fieldName, wasmTable);
					cb.invokestatic(wasmTable, "table_grow", MethodTypeDesc.of(at, CD_Object, at, wasmTable));
					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.add(typeKind(at));
				}
				case TableInstr.Table_Fill(var tableIdx) -> {
					var tableInfo = tables.get(tableIdx.index());
					var at = addrDesc(tableInfo.tableType.addrType());

					cb.aload(0);
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

					cb.aload(0);
					cb.getfield(className, destTable.fieldName, wasmTable);

					cb.aload(0);
					cb.getfield(className, srcTable.fieldName, wasmTable);

					cb.invokestatic(
						wasmTable,
						"copy",
						MethodTypeDesc.of(
							CD_void,
							dat,
							sat,
							minAt,
							wasmTable,
							wasmTable
						)
					);
					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.removeLast();
				}
				case TableInstr.Table_Init(var tableIdx, var elemIdx) -> {
					var table = tables.get(tableIdx.index());
					var elem = elems.get(elemIdx.index());
					Objects.requireNonNull(elem);

					var at = addrDesc(table.tableType.addrType());

					cb.aload(0);
					cb.getfield(className, elem.fieldName, elem.fieldType);

					cb.aload(0);
					cb.getfield(className, table.fieldName, wasmTable);

					cb.invokestatic(
						wasmTable,
						"copyFromArray",
						MethodTypeDesc.of(
							CD_void,
							at,
							CD_int,
							CD_int,
							CD_Object.arrayType(),
							wasmTable
						)
					);
					stackTypes.removeLast();
					stackTypes.removeLast();
					stackTypes.removeLast();
				}
				case TableInstr.Elem_Drop(var elemIdx) -> {
					var elem = elems.get(elemIdx.index());
					Objects.requireNonNull(elem);
					cb.aload(0);
					cb.iconst_0();
					cb.anewarray(elem.elementType);
					cb.putfield(className, elem.fieldName, elem.fieldType);
				}
			}
		}

		private void generateVariableInstr(VariableInstr instr) {
			switch(instr) {
				case VariableInstr.Local_Get(var localIdx) -> {
					var localInfo = locals[localIdx.index()];
					var t = typeKind(localInfo.type());
					cb.loadLocal(t, localInfo.slotIndex());
					stackTypes.add(t);
				}
				case VariableInstr.Local_Set(var localIdx) -> {
					var localInfo = locals[localIdx.index()];
					cb.storeLocal(typeKind(localInfo.type()), localInfo.slotIndex());
					stackTypes.removeLast();
				}
				case VariableInstr.Local_Tee(var localIdx) -> {
					var localInfo = locals[localIdx.index()];
					if(typeKind(localInfo.type).slotSize() == 2) {
						cb.dup2();
					}
					else {
						cb.dup();
					}
					cb.storeLocal(typeKind(localInfo.type()), localInfo.slotIndex());
				}
				case VariableInstr.Global_Get(var globalIdx) -> {
					var globalInfo = globals.get(globalIdx.index());


					cb.aload(0);
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

					stackTypes.add(typeKind(globalInfo.elementType));
				}
				case VariableInstr.Global_Set(var globalIdx) -> {
					var globalInfo = globals.get(globalIdx.index());

					cb.aload(0);
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
				case VectorInstr.I8x16_Op_Instr(var op) -> {
					switch(op) {
						case VectorInstr.Swizzle() -> {
							cb.invokevirtual(v128Type, "swizzle8", MethodTypeDesc.of(v128Type, v128Type));
							stackTypes.removeLast();
						}
						case VectorInstr.Shuffle(var laneIndexes) -> {
							loadV128(laneIndexes);
							cb.invokevirtual(v128Type, "shuffle8", MethodTypeDesc.of(v128Type, v128Type, v128Type));
							stackTypes.removeLast();
							stackTypes.removeLast();
						}
						case VectorInstr.ExtractLane_U(var laneIdx) -> {
							cb.loadConstant(laneIdx);
							cb.invokevirtual(v128Type, "extractLane8", MethodTypeDesc.of(CD_byte, v128Type, CD_int));
							cb.invokestatic(CD_Byte, "toUnsignedInt", MethodTypeDesc.ofDescriptor("(B)I"));
							stackTypes.removeLast();
							stackTypes.add(TypeKind.INT);
						}
						case VectorInstr.ExtractLane_S(var laneIdx) -> {
							cb.loadConstant(laneIdx);
							cb.invokevirtual(v128Type, "extractLane8", MethodTypeDesc.of(CD_byte, CD_int));
							stackTypes.removeLast();
							stackTypes.add(TypeKind.INT);
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
							cb.invokevirtual(v128Type, "popcnt8", MethodTypeDesc.of(CD_byte));
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
							cb.invokevirtual(v128Type, "extractLane16", MethodTypeDesc.of(CD_short, v128Type, CD_int));
							cb.invokestatic(CD_Short, "toUnsignedInt", MethodTypeDesc.ofDescriptor("(S)I"));
							stackTypes.removeLast();
							stackTypes.add(TypeKind.INT);
						}
						case VectorInstr.ExtractLane_S(var laneIdx) -> {
							cb.loadConstant(laneIdx);
							cb.invokevirtual(v128Type, "extractLane16", MethodTypeDesc.of(CD_short, v128Type, CD_int));
							stackTypes.removeLast();
							stackTypes.add(TypeKind.INT);
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
									cb.invokevirtual(v128Type, "avgrU8", MethodTypeDesc.of(v128Type, v128Type));
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

//				case VectorInstr.F32x4_Op_Instr f32x4OpInstr -> {
//				}
//				case VectorInstr.F32x4_Ternary_Op_Instr f32x4TernaryOpInstr -> {
//				}
//				case VectorInstr.F64x2_Op_Instr f64x2OpInstr -> {
//				}
//				case VectorInstr.F64x2_Ternary_Op_Instr f64x2TernaryOpInstr -> {
//				}
//				case VectorInstr.I16x8_Relaxed_Dot_I8x16_I7x16_S i16x8RelaxedDotI8x16I7x16S -> {
//				}
//				case VectorInstr.I32x4_Op_Instr i32x4OpInstr -> {
//				}
//				case VectorInstr.I32x4_Relaxed_Dot_I8x16_I7x16_Add_S i32x4RelaxedDotI8x16I7x16AddS -> {
//				}
//				case VectorInstr.I64x2_Op_Instr i64x2OpInstr -> {
//				}
//				case VectorInstr.V128_Const v128Const -> {
//				}
//				case VectorInstr.VVBinOp vvBinOp -> {
//				}
//				case VectorInstr.VVTernOp vvTernOp -> {
//				}
//				case VectorInstr.VVTestOp vvTestOp -> {
//				}
//				case VectorInstr.VVUnOp vvUnOp -> {
//				}

				default -> throw new RuntimeException("Not implemented: " + instr);
			}
		}

		private void generateVectorIOp(VectorInstr.VIOp op, String sizeSuffix, ClassDesc laneType) {
			switch(op) {
				case VectorInstr.ReplaceLane(var laneIdx) -> {
					cb.loadConstant(laneIdx);
					cb.invokevirtual(v128Type, "replaceLane" + sizeSuffix, MethodTypeDesc.of(laneType, CD_int));
					stackTypes.removeLast();
					stackTypes.add(typeKind(laneType).asLoadable());
				}
				case VectorInstr.Splat() -> {
					cb.invokestatic(v128Type, "splat" + sizeSuffix, MethodTypeDesc.of(v128Type, laneType));
					stackTypes.removeLast();
					stackTypes.add(TypeKind.REFERENCE);
				}

				case VectorInstr.All_True() -> {
					cb.invokestatic(v128Type, "allTrue" + sizeSuffix, MethodTypeDesc.of(v128Type, CD_int));
				}
				case VectorInstr.BitMask() -> {
					cb.invokestatic(v128Type, "bitMask" + sizeSuffix, MethodTypeDesc.of(v128Type, CD_int));
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

		private void generateReturn() {
			var resultArgTypes = new ArrayList<ClassDesc>();

			for(var t : returnType.types()) {
				var realization = compiler.getValType(t);
				resultArgTypes.add(realization.type());
			}

			var returnTypeClass = compiler.getResultType(returnType);

			cb.invokestatic(compiler.getResultType(returnType), "of", MethodTypeDesc.of(returnTypeClass, resultArgTypes), true);
			cb.areturn();

			isUnreachable = true;
			stackTypes.clear();
		}

		private LabelInfo getLabel(LabelIdx labelIdx) {
			return labels.get(labels.size() - 1 - labelIdx.index());
		}

		private void unpackResultType(ResultType resultType, ClassDesc endResultClass) {
			if(resultType.types().isEmpty()) {
				cb.pop();
				return;
			}

			for(int i = 0; i < resultType.types().size(); ++i) {
				var t = compiler.getValType(resultType.types().get(i)).type();

				stackTypes.add(typeKind(t));

				boolean isLast = i == resultType.types().size() - 1;
				if(!isLast) {
					cb.dup();
				}

				cb.getfield(endResultClass, "item" + i, t);

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

		private record LabelInfo(Label label, ResultType labelType) {}

		private record StashedBlockState(int oldTempVarSlot, List<StashedStackValue> stashedStackValues) {}
		private record StashedStackValue(int slot, TypeKind typeKind) {}

		private StashedBlockState enterBlock(FuncType type) {
			if(stackTypes.size() == type.args().types().size()) {
				return new StashedBlockState(tempVarSlot, List.of());
			}

			int oldTempVarSlot = tempVarSlot;
			int stashCount = stackTypes.size() - type.args().types().size();

			List<StashedStackValue> stashedStackValues = new ArrayList<>();
			for(var t : stackTypes) {
				stashedStackValues.add(new StashedStackValue(tempVarSlot, t));
				tempVarSlot += t.slotSize();
			}

			for(var value : stashedStackValues.reversed()) {
				cb.storeLocal(value.typeKind, value.slot);
			}

			var stashedArgs = stashedStackValues.subList(stashCount, stashedStackValues.size());
			for(var stashedArg : stashedArgs) {
				cb.loadLocal(stashedArg.typeKind, stashedArg.slot);

				// A bit odd because we can't actually discard the variables until
				// after all instructions from the loop have run.
				// But this value won't matter until after the loop anyway.
				tempVarSlot -= stashedArg.typeKind.slotSize();
			}

			stashedArgs.clear();
			stackTypes.clear();

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
					stackTypes.add(typeKind(compiler.getValType(t).type()));
				}
			}
			else {
				var stashedValues = new ArrayList<>(state.stashedStackValues);
				for(var resType : type.results().types()) {
					var javaType  = compiler.getValType(resType).type();
					var t = typeKind(javaType);
					stashedValues.add(new StashedStackValue(tempVarSlot, t));
					tempVarSlot += t.slotSize();
				}

				for(var resStash : stashedValues.subList(state.stashedStackValues.size(), stashedValues.size()).reversed()) {
					cb.storeLocal(resStash.typeKind, resStash.slot);
				}

				for(var stashedValue : stashedValues) {
					cb.loadLocal(stashedValue.typeKind, stashedValue.slot);
					stackTypes.add(stashedValue.typeKind);
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
				tempVarSlot += t.slotSize();
			}

			var localStackTypes = new ArrayList<>(stackTypes);

			for(var resStash : stashedValues.reversed()) {
				cb.storeLocal(resStash.typeKind, resStash.slot);
				localStackTypes.removeLast();
			}

			while(!localStackTypes.isEmpty()) {
				var t = localStackTypes.getLast();
				localStackTypes.removeLast();

				if(t.slotSize() == 2) {
					cb.pop2();
				}
				else {
					cb.pop();
				}
			}

			for(var stashedValue : stashedValues) {
				cb.loadLocal(stashedValue.typeKind, stashedValue.slot);
				localStackTypes.add(stashedValue.typeKind);
			}

			tempVarSlot = oldTempVarSlot;
		}

		private void loadMem(MemoryInstr.MemArg memArg) {
			loadMem(memArg.memIdx());
		}

		private void loadMem(MemIdx memIdx) {
			cb.aload(0);
			cb.getfield(className, mems.get(memIdx.index()).fieldName, wasmMemory);
		}

		private void doLoad(ClassDesc t, MemoryInstr.MemArg memArg) {
			doLoadStore(true, t, memArg);
			stackTypes.removeLast();
			stackTypes.add(typeKind(t).asLoadable());
		}

		private void doStore(ClassDesc t, MemoryInstr.MemArg memArg) {
			doLoadStore(false, t, memArg);
			stackTypes.removeLast();
			stackTypes.removeLast();
		}

		private void doLoadStore(boolean isLoad, ClassDesc t, MemoryInstr.MemArg memArg) {
			String prefix = isLoad ? "load" : "store";

			String suffix;
			if(t == CD_byte) {
				suffix = "I8";
			}
			else if(t == CD_short) {
				suffix = "I16";
			}
			else if(t == CD_int) {
				suffix = "I32";
			}
			else if(t == CD_long) {
				suffix = "I64";
			}
			else if(t == CD_float) {
				suffix = "F32";
			}
			else if(t == CD_double) {
				suffix = "F64";
			}
			else if(t.equals(ClassDesc.of(RUNTIME_PACKAGE, "V128"))) {
				suffix = "V128";
			}
			else {
				throw new RuntimeException("Unexpected elementType for memory load");
			}

			var mem = mems.get(memArg.memIdx().index());
			var at = addrDesc(mem.memType.addrType());

			switch(mem.memType.addrType()) {
				case I32 -> cb.loadConstant((int)memArg.offset());
				case I64 -> cb.loadConstant(memArg.offset());
			}

			loadMem(memArg);

			cb.invokestatic(
				wasmMemory,
				prefix + suffix,
				isLoad
					? MethodTypeDesc.of(
						t,
						at,
						at,
						wasmMemory
					)
					: MethodTypeDesc.of(
						CD_void,
						at,
						t,
						at,
						wasmMemory
					)
			);
		}


		private void saveStackTempRes(ResultType types) {
			saveStackTempDesc(Lists.transform(types.types(), arg -> compiler.getValType(arg).type()));
		}

		private void saveStackTempDesc(List<ClassDesc> types) {
			saveStackTemp(Lists.transform(types, WasmClassGeneratorUtils::typeKind));
		}

		private void saveStackTemp(List<TypeKind> tempTypes) {
			for(var t : tempTypes) {
				tempVarSlot += t.slotSize();
			}

			int slot = tempVarSlot;
			for(var t : tempTypes.reversed()) {
				slot -= t.slotSize();
				cb.storeLocal(t, slot);
				stackTypes.removeLast();
			}
		}

		private void restoreStackTempRes(ResultType types) {
			restoreStackTempDesc(Lists.transform(types.types(), arg -> compiler.getValType(arg).type()));
		}

		private void restoreStackTempDesc(List<ClassDesc> types) {
			restoreStackTemp(Lists.transform(types, WasmClassGeneratorUtils::typeKind));
		}

		private void restoreStackTemp(List<TypeKind> tempTypes) {
			for(var t : tempTypes.reversed()) {
				tempVarSlot -= t.slotSize();
			}

			int slot = tempVarSlot;
			for(var t : tempTypes) {
				cb.loadLocal(t, slot);
				slot += t.slotSize();
				stackTypes.add(t);
			}
		}

		private void loadV128(V128 value) {
			cb.new_(v128Type);
			cb.dup();
			for(int i = 0; i < 16; ++i) {
				cb.loadConstant(value.extractLane8(i));
			}
			cb.invokevirtual(v128Type, "<init>", MethodTypeDesc.of(CD_void, Collections.nCopies(16, v128Type)));
		}
	}



	private void generateFunctionImport(ClassBuilder clb, ImportModuleInfo imp, ImportDesc.Func func, String localName, MethodTypeDesc type, WasmExportRealization.OfInstanceMethod exportRealization) {
		clb.withMethodBody(localName, type, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL, cb -> {
			cb.aload(0);
			cb.getfield(className, imp.fieldName, imp.realization().classDesc());

			int slotOffset = 1;
			for(int i = 0; i < type.parameterCount(); ++i) {
				var paramType = type.parameterType(i);
				cb.loadLocal(typeKind(paramType), slotOffset);
				slotOffset += slotSize(paramType);
			}

			cb.invokevirtual(imp.realization().classDesc(), exportRealization.methodName(), exportRealization.methodType());
			cb.areturn();
		});

		generateStaticThunk(clb, localName, type);
	}

	private FuncType getFuncType(DefType t) {
		var subtype = TypeUnroll.unroll(t);
		return switch(subtype.compositeType()) {
			case AggregateType _ -> throw new RuntimeException("Unexpected aggregate elementType");
			case FuncType ft -> ft;
		};
	}

	private FuncType getBlockFuncType(ControlInstr.BlockType t) {
		return switch(t) {
			case ControlInstr.BlockType.Empty() -> new FuncType(new ResultType(ImmutableList.of()), new ResultType(ImmutableList.of()));
			case ControlInstr.BlockType.OfIndex(var typeIdx)  -> getFuncType(types.get(typeIdx.index()));
			case ControlInstr.BlockType.OfValType(var valType) -> new FuncType(new ResultType(ImmutableList.of()), new ResultType(ImmutableList.of(valType)));
		};
	}

	private ClassDesc getGlobalContainerType(GlobalType globalType, ClassDesc elementType) {
		return switch(globalType.mutability()) {
			case Const -> elementType;
			case Var -> ClassDesc.of(
				RUNTIME_PACKAGE,
				switch(globalType.type()) {
					case NumType numType -> switch(numType) {
						case I32 -> "GlobalI32";
						case I64 -> "GlobalI64";
						case F32 -> "GlobalF32";
						case F64 -> "GlobalF64";
					};
					default -> "GlobalRef";
				}
			);
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

	private record FunctionInfo(String name, MethodTypeDesc type, FuncType funcType, DefType defType) {}

	private record TableInfo(String fieldName, ClassDesc elementType, TableType tableType) {}

	private record GlobalInfo(String fieldName, ClassDesc containerType, ClassDesc elementType, GlobalType globalType) {}

	private record MemInfo(String fieldName, MemType memType) {}

	private record ElemInfo(String fieldName, ClassDesc fieldType, ClassDesc elementType, Elem elem) {}

	private record TagInfo(
		TagRealization realization,
		FuncType funcType,
		DefType defType
	) {}

	private record LocalInfo(int slotIndex, ClassDesc type) {}
}
