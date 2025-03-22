package dev.argon.jawawasm.engine.compiler;

import dev.argon.jawawasm.engine.ModuleResolver;
import dev.argon.jawawasm.engine.internal.TypeClosure;
import dev.argon.jawawasm.engine.internal.TypeRoll;
import dev.argon.jawawasm.format.instructions.*;
import dev.argon.jawawasm.format.modules.*;
import dev.argon.jawawasm.format.modules.Module;
import dev.argon.jawawasm.format.types.*;
import dev.argon.jawawasm.runtime.AddrType;
import dev.argon.jawawasm.runtime.ModuleResolutionException;
import org.jspecify.annotations.Nullable;

import java.lang.classfile.*;
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
	private final List<ImportInfo> imports = new ArrayList<>();
	private final List<FunctionInfo> funcs = new ArrayList<>();
	private final List<TableInfo> tables = new ArrayList<>();
	private final List<GlobalInfo> globals = new ArrayList<>();
	private final List<MemInfo> mems = new ArrayList<>();
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
			List.copyOf(exports)
		);
	}

	@Override
	protected byte[] generateImpl() throws ModuleResolutionException {
		if(cachedBytecode == null) {
			try {
				cachedBytecode = compiler.getOptions().classFile().build(className(), this::buildClass);
			}
			catch(UncheckedModuleResolutionException e) {
				throw e.getCause();
			}
		}
		return cachedBytecode;
	}


	private final TypeClosure closure = new TypeClosure() {
		@Override
		public HeapType resolveTypeIdx(TypeIdx idx) {
			return resolveHeapType(types.get(idx.index()));
		}
	};


	private void buildClass(ClassBuilder clb) {
		types.clear();
		imports.clear();
		funcs.clear();
		tables.clear();
		globals.clear();

		clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL);
		clb.withSuperclass(wasmModuleClass);

		for(var recType : module.types()) {
			var rolledRecType = TypeRoll.roll(recType, types.size());
			for(int i = 0; i < recType.subtypes().size(); ++i) {
				types.add(new DefType(rolledRecType, i));
			}
		}

		List<Consumer<CodeBuilder>> constructorInits = new ArrayList<>();
		List<ClassDesc> constructorParams = new ArrayList<>();

		constructorParams.add(ClassDesc.of(RUNTIME_PACKAGE, "RuntimeContext"));

		int constructorParamSlot = 2;


		for(var imp : module.imports()) {
			int importIndex0 = importModules.indexOf(imp.module());
			ImportModuleInfo importModuleInfo;
			if(importIndex0 < 0) {
				importIndex0 = importModules.size();
				importModules.add(imp.module());

				WasmModuleRealization importRealization;
				try {
					importRealization = resolver.resolve(imp.module());
				} catch(ModuleResolutionException e) {
					throw new UncheckedModuleResolutionException(e);
				}
				importModuleInfo = new ImportModuleInfo(imp.module(), "import" + importIndex0, importRealization);
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
			int importIndex = importIndex0;

			String localName;

			var exportRealization = importModuleInfo.realization.exports().stream()
				.filter(e -> e.exportName().equals(imp.name()))
				.findFirst()
				.get();

			switch(imp.desc()) {
				case ImportDesc.Func func -> {
					localName = "func" + funcs.size();
					var defType = types.get(func.type().index());
					var type = compiler.getMethodType(defType);
					funcs.add(new FunctionInfo(localName, type, getFuncType(defType), defType));
					generateFunctionImport(clb, imp, func, localName, type, exportRealization);
				}
				case ImportDesc.Table table -> {
					localName = "table" + tables.size();
					var tableType = closure.resolveTableType(table.type());
					var elementType = compiler.getValType(tableType.elementType()).type();

					tables.add(new TableInfo(localName, elementType, tableType));
					clb.withField(localName, wasmTable, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);

					constructorInits.add(cb -> {
						cb.aload(0);
						cb.aload(0);
						cb.getfield(className, importModuleInfo.fieldName, importModuleInfo.realization().classDesc());
						cb.invokevirtual(importModuleInfo.realization().classDesc(), exportRealization.methodName(), exportRealization.methodType());
						cb.putfield(className, localName, wasmTable);
					});
				}
				case ImportDesc.Global global -> {
					localName = "global" + globals.size();

					var globalType = closure.resolveGlobalType(global.type());
					ClassDesc elementType = compiler.getValType(globalType.type()).type();
					ClassDesc containerType = getGlobalContainerType(globalType, elementType);
					globals.add(new GlobalInfo(localName, containerType, elementType, globalType));

					clb.withField(localName, containerType, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);

					constructorInits.add(cb -> {
						cb.aload(0);
						cb.aload(0);
						cb.getfield(className, importModuleInfo.fieldName, importModuleInfo.realization().classDesc());
						cb.invokevirtual(importModuleInfo.realization().classDesc(), exportRealization.methodName(), exportRealization.methodType());
						cb.putfield(className, localName, containerType);
					});
				}
				case ImportDesc.Mem mem -> {
					localName = "mem" + mems.size();
					mems.add(new MemInfo(localName, mem.type()));

					clb.withField(localName, wasmMemory, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);

					constructorInits.add(cb -> {
						cb.aload(0);
						cb.getfield(className, importModuleInfo.fieldName, importModuleInfo.realization().classDesc());
						cb.invokevirtual(importModuleInfo.realization().classDesc(), exportRealization.methodName(), exportRealization.methodType());
						cb.putfield(className, localName, wasmMemory);
					});
				}
				case ImportDesc.Tag tag -> {
					throw new RuntimeException("Not implemented");
				}
			}

			imports.add(new ImportInfo(imp.module(), imp.name(), localName, imp.desc()));
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
				}

				var bytecodeGen = new BytecodeGenerator(cb, new LocalInfo[] {}, new ResultType(List.of(globalType.type())), 0);
				bytecodeGen.generateInstructionBlock(global.init());

				if(globalType.mutability() == Mut.Var) {
					cb.invokespecial(containerType, "<init>", MethodTypeDesc.of(CD_void, elementType));
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

				var bytecodeGen = new BytecodeGenerator(cb, new LocalInfo[] {}, new ResultType(List.of(table.type().elementType())), 0);
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
			if(elem.mode() instanceof ElemMode.Declarative) {
				elems.add(null);
			}

			var fieldName = "elem" + elems.size();

			var elementType = compiler.getValType(elem.type()).type();
			var arrayType = elementType.arrayType();

			elems.add(new ElemInfo(fieldName, arrayType, elementType, elem));

			clb.withField(fieldName, arrayType, ClassFile.ACC_PRIVATE);
			constructorInits.add(cb -> {
				cb.aload(0);
				cb.loadConstant(elem.init().size());
				cb.anewarray(elementType);
				cb.putfield(className, fieldName, arrayType);

				for(int i = 0; i < elem.init().size(); ++i) {
					cb.aload(0);
					cb.getfield(className, fieldName, arrayType);
					cb.loadConstant(i);

					var bytecodeGen = new BytecodeGenerator(cb, new LocalInfo[] {}, new ResultType(List.of(elem.type())), 0);
					bytecodeGen.generateInstructionBlock(elem.init().get(i));

					cb.aastore();
				}

				if(elem.mode() instanceof ElemMode.Active(var tableIdx, var offset)) {
					var table = tables.get(tableIdx.index());
					var at = addrDesc(table.tableType.addrType());

					var bytecodeGen = new BytecodeGenerator(cb, new LocalInfo[] {}, new ResultType(List.of(addrValType(table.tableType.addrType()))), 0);
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
							at,
							at,
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

					var bytecodeGen = new BytecodeGenerator(cb, new LocalInfo[]{}, new ResultType(List.of(addrValType(mem.memType().addrType()))), 0);
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
							at,
							at,
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
					return data.init().clone();
				}
			});
		}

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
					exports.add(new WasmExportRealization(export.name(), methodName, methodType, funcInfo.defType));
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
							cb.areturn();
						}
					);
					exports.add(new WasmExportRealization(export.name(), methodName, methodType, globalInfo.globalType()));
				}
				case ExportDesc.Mem mem -> throw new RuntimeException("Not implemented");
				case ExportDesc.Table table -> throw new RuntimeException("Not implemented");
				case ExportDesc.Tag tag -> throw new RuntimeException("Not implemented");
			}
		}
	}

	private static void generateConstructor(ClassBuilder clb, List<Consumer<CodeBuilder>> constructorInits, List<ClassDesc> constructorParams) {
		var ctorType = MethodTypeDesc.of(CD_void, constructorParams);

		clb.withMethodBody("<init>", ctorType, ClassFile.ACC_PUBLIC, cb -> {
			cb.aload(0).invokespecial(wasmModuleClass, "<init>", MethodTypeDesc.ofDescriptor("()V"));

			for(var ctorInit : constructorInits) {
				ctorInit.accept(cb);
			}

			cb.return_();
		});
	}

	private void generateFunction(ClassBuilder clb, String name, MethodTypeDesc type, Func func) {
		System.err.println("generateFunction " + name);
		clb.withMethodBody(name, type, ClassFile.ACC_PRIVATE, cb -> {
			var funcType = types.get(func.type().index());

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
				var localType = compiler.getValType(func.locals().get(i));
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
		private boolean usesReturnLabel = false;

		public void generateFunctionBody(Expr body) {
			var returnLabel = cb.newLabel();
			labels.add(new LabelInfo(returnLabel, returnType));
			generateInstructionBlock(body);

			if(!isUnreachable || usesReturnLabel) {
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
				case VariableInstr variableInstr -> generateVaiableInstr(variableInstr);
				case VectorInstr vectorInstr -> throw new RuntimeException("Not implemented");
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


				case ControlInstr.Br(var labelIdx) -> {
					var label = getLabel(labelIdx);
					doJump(label.label, label.labelType, Opcode.GOTO, Opcode.NOP);

					isUnreachable = true;
					stackTypes.clear();
				}
				case ControlInstr.Br_If(var labelIdx) -> {
					var label = getLabel(labelIdx);
					stackTypes.removeLast();
					doJump(label.label, label.labelType, Opcode.IFNE, Opcode.IFEQ);
				}
				case ControlInstr.Br_Table(var table, var fallbackIdx) -> {
					if(table.isEmpty()) {
						cb.pop();
						generateControlInstr(new ControlInstr.Br(fallbackIdx));
						return;
					}

					var fallbackLabel = getLabel(fallbackIdx);

					cb.tableswitch(0, table.size() - 1, fallbackLabel.label);
				}
				case ControlInstr.Br_OnNonNull(var labelIdx) -> {
					var label = getLabel(labelIdx);

					var nullLabel = cb.newLabel();

					cb.dup();
					cb.ifnull(nullLabel);
					doJump(label.label, label.labelType, Opcode.GOTO, Opcode.NOP);
					cb.labelBinding(nullLabel);
					cb.pop();
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
					exitBlock(type, state, exitElseLabel);
					isUnreachable &= thenUnreachable;

					cb.labelBinding(endLabel);
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
					var defType = types.get(funcTypeIdx.index());
					var realizedType = (FuncTypeRealization)compiler.getDefType(defType);
					var funcType = getFuncType(defType);

					int tempVarSlot = this.tempVarSlot;
					for(var paramType : realizedType.methodType().parameterList()) {
						tempVarSlot += typeKind(paramType).slotSize();
					}

					int functionObjSlot = tempVarSlot;
					cb.astore(functionObjSlot);
					stackTypes.removeLast();

					for(var paramType : realizedType.methodType().parameterList().reversed()) {
						tempVarSlot -= typeKind(paramType).slotSize();
						cb.storeLocal(typeKind(paramType), tempVarSlot);
						stackTypes.removeLast();
					}

					cb.aload(functionObjSlot);

					for(var paramType : realizedType.methodType().parameterList()) {
						cb.loadLocal(typeKind(paramType), tempVarSlot);
						tempVarSlot += typeKind(paramType).slotSize();
					}

					var resultType = realizedType.methodType().returnType();
					var endResultType = resultType.nested("EndResult");

					cb.invokeinterface(realizedType.classDesc(), realizedType.methodName(), realizedType.methodType());
					cb.invokestatic(resultType, "get", MethodTypeDesc.of(endResultType, resultType), true);
					unpackResultType(funcType.results(), endResultType);
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

//				case ControlInstr.Br_OnCast brOnCast -> {
//				}
//				case ControlInstr.Br_OnCastFail brOnCastFail -> {
//				}
//				case ControlInstr.Br_OnNull brOnNull -> {
//				}
//				case ControlInstr.Loop loop -> {
//				}
//				case ControlInstr.Return_Call returnCall -> {
//				}
//				case ControlInstr.Return_Call_Indirect returnCallIndirect -> {
//				}
//				case ControlInstr.Return_Call_Ref returnCallRef -> {
//				}
//				case ControlInstr.Throw aThrow -> {
//				}
//				case ControlInstr.Throw_Ref throwRef -> {
//				}
//				case ControlInstr.Try_Table tryTable -> {
//				}
//				case ControlInstr.Unreachable unreachable -> {
//				}

				default -> throw new RuntimeException("Not implemented: " + instr);
			}
		}

		private void generateMemoryInstr(MemoryInstr instr) {
			switch(instr) {
				case MemoryInstr.Inn_Load(var numSize, var memArg) -> {
					doLoad(numSizeDesc(numSize), memArg);
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
					var at = addrDesc(dstMem.memType.addrType());
					cb.aload(0);
					cb.getfield(className, dstMem.fieldName, wasmMemory);
					cb.aload(0);
					cb.getfield(className, srcMem.fieldName, wasmMemory);
					cb.invokestatic(
						wasmMemory,
						"copy",
						MethodTypeDesc.of(
							CD_void,
							at,
							at,
							at,
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

//				case MemoryInstr.Fnn_Load fnnLoad -> {
//				}
//				case MemoryInstr.Fnn_Store fnnStore -> {
//				}
//				case MemoryInstr.I64_Load32_S i64Load32S -> {
//				}
//				case MemoryInstr.I64_Load32_U i64Load32U -> {
//				}
//				case MemoryInstr.I64_Store32 i64Store32 -> {
//				}
//				case MemoryInstr.Inn_Load16_S innLoad16S -> {
//				}
//				case MemoryInstr.Inn_Load16_U innLoad16U -> {
//				}
//				case MemoryInstr.Inn_Store innStore -> {
//				}
//				case MemoryInstr.Inn_Store16 innStore16 -> {
//				}
//				case MemoryInstr.Inn_Store8 innStore8 -> {
//				}
//				case MemoryInstr.Memory_Grow memoryGrow -> {
//				}
//				case MemoryInstr.Memory_Size memorySize -> {
//				}
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
					cb.loadConstant(value);
					stackTypes.add(TypeKind.LONG);
				}
				case NumericInstr.F32_Const(var value) -> {
					cb.loadConstant(value);
					stackTypes.add(TypeKind.FLOAT);
				}
				case NumericInstr.F64_Const(var value) -> {
					cb.loadConstant(value);
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
						case _64 -> cb.invokestatic(CD_Long, methodName, MethodTypeDesc.ofDescriptor("(J)J"));
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
								case _64 -> cb.invokestatic(CD_Long, "rotateLeft", MethodTypeDesc.ofDescriptor("(JI)J"));
							}
						}
						case ROTR -> {
							switch(size) {
								case _32 -> cb.invokestatic(CD_Integer, "rotateRight", descriptor);
								case _64 -> cb.invokestatic(CD_Long, "rotateRight", MethodTypeDesc.ofDescriptor("(JI)J"));
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

//				case NumericInstr.F32_Demote_F64 f32DemoteF64 -> {
//				}
//				case NumericInstr.F64_Promote_F32 f64PromoteF32 -> {
//				}
//				case NumericInstr.Fnn_Convert_Imm_S fnnConvertImmS -> {
//				}
//				case NumericInstr.Fnn_Convert_Imm_U fnnConvertImmU -> {
//				}
//				case NumericInstr.Fnn_FRelOp fnnFRelOp -> {
//				}
//				case NumericInstr.Fnn_Reinterpret_Inn fnnReinterpretInn -> {
//				}
//				case NumericInstr.Inn_Reinterpret_Fnn innReinterpretFnn -> {
//				}
//				case NumericInstr.Inn_Trunc_Fmm_S innTruncFmmS -> {
//				}
//				case NumericInstr.Inn_Trunc_Fmm_U innTruncFmmU -> {
//				}
//				case NumericInstr.Inn_Trunc_Sat_Fmm_S innTruncSatFmmS -> {
//				}
//				case NumericInstr.Inn_Trunc_Sat_Fmm_U innTruncSatFmmU -> {
//				}

				default -> throw new RuntimeException("Not implemented: " + instr);
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
						cb.dup2_x2();
						cb.pop2();
						cb.pop2();
					}
					else {
						cb.swap();
					}

					cb.goto_(endLabel);

					cb.labelBinding(bottomLabel);
					if(t.slotSize() == 2) {
						cb.pop2();
					}
					else {
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

						realizedFuncType.methodType(),
						MethodHandleDesc.ofMethod(
							DirectMethodHandleDesc.Kind.VIRTUAL,
							className,
							funcInfo.name,
							funcInfo.type
						),
						realizedFuncType.methodType()
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
//				case ReferenceInstr.Ref_AsNonNull refAsNonNull -> {
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

				case TableInstr.Table_Copy(var destTableIdx, var srcTableIdx) -> {
					var destTable = tables.get(destTableIdx.index());
					var srcTable = tables.get(srcTableIdx.index());

					var at = addrDesc(destTable.tableType.addrType());

					cb.aload(0);
					cb.getfield(className, destTable.fieldName, wasmTable);

					cb.aload(0);
					cb.getfield(className, srcTable.fieldName, wasmTable);

					cb.invokestatic(
						wasmTable,
						"copy",
						MethodTypeDesc.of(
							CD_void,
							at,
							at,
							at,
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

//				case TableInstr.Table_Fill tableFill -> {
//				}
//				case TableInstr.Table_Grow tableGrow -> {
//				}
//				case TableInstr.Table_Size tableSize -> {
//				}

				default -> throw new RuntimeException("Not implemented: " + instr);
			}
		}

		private void generateVaiableInstr(VariableInstr instr) {
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
					cb.dup();
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
				case VariableInstr.Global_Set globalSet -> throw new RuntimeException("Not implemented");
			}
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
			if(labelIdx.index() == labels.size() - 1) {
				usesReturnLabel = true;
			}
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

			if(!state.stashedStackValues.isEmpty()) {
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

			for(var t : type.results().types()) {
				stackTypes.add(typeKind(compiler.getValType(t).type()));
			}

		}

		private void doJump(Label label, ResultType labelType, Opcode jump, Opcode inverseJump) {
			jumpedLabels.add(label);

			if(stackTypes.size() == labelType.types().size()) {
				cb.branch(jump, label);
				return;
			}

			int oldTempVarSlot = tempVarSlot;
			var stashedValues = new ArrayList<StashedStackValue>();
			for(var resType : labelType.types()) {
				var javaType = compiler.getValType(resType).type();
				var t = typeKind(javaType);
				stashedValues.add(new StashedStackValue(tempVarSlot, t));
				tempVarSlot += t.slotSize();
			}

			var isUnconditional = jump == Opcode.GOTO || jump == Opcode.GOTO_W;

			var afterJumpLabel = cb.newLabel();

			if(!isUnconditional) {
				cb.branch(inverseJump, afterJumpLabel);
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

			cb.goto_(label);

			cb.labelBinding(afterJumpLabel);
		}

		private void loadMem(MemoryInstr.MemArg memArg) {
			cb.aload(0);
			cb.getfield(className, mems.get(memArg.memIdx().index()).fieldName, wasmMemory);
		}

		private void doLoad(ClassDesc t, MemoryInstr.MemArg memArg) {
			doLoadStore("load", t, memArg);
			stackTypes.removeLast();
			stackTypes.add(typeKind(t).asLoadable());
		}

		private void doStore(ClassDesc t, MemoryInstr.MemArg memArg) {
			doLoadStore("store", t, memArg);
			stackTypes.removeLast();
			stackTypes.removeLast();
		}

		private void doLoadStore(String prefix, ClassDesc t, MemoryInstr.MemArg memArg) {
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
				MethodTypeDesc.of(
					t,
					at,
					at,
					wasmMemory
				)
			);
		}
	}



	private void generateFunctionImport(ClassBuilder clb, Import imp, ImportDesc.Func func, String localName, MethodTypeDesc type, WasmExportRealization exportRealization) {
		clb.withMethodBody(localName, type, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL, cb -> {
			throw new RuntimeException("Not implemented");
		});
	}

	private FuncType getFuncType(DefType t) {
		var subtype = t.recursiveType().subtypes().get(t.index());
		return switch(subtype.compositeType()) {
			case AggregateType _ -> throw new RuntimeException("Unexpected aggregate elementType");
			case FuncType ft -> ft;
		};
	}

	private FuncType getBlockFuncType(ControlInstr.BlockType t) {
		return switch(t) {
			case ControlInstr.BlockType.Empty() -> new FuncType(new ResultType(List.of()), new ResultType(List.of()));
			case ControlInstr.BlockType.OfIndex(var typeIdx)  -> getFuncType(types.get(typeIdx.index()));
			case ControlInstr.BlockType.OfValType(var valType) -> new FuncType(new ResultType(List.of()), new ResultType(List.of(valType)));
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

	private ClassDesc numSizeDesc(NumericInstr.NumSize numSize) {
		return switch(numSize) {
			case _32 -> CD_int;
			case _64 -> CD_long;
		};
	}

	private record ImportModuleInfo(String module, String fieldName, WasmModuleRealization realization) {}
	private record ImportInfo(String module, String importedName, String localName, ImportDesc desc) {}

	private record FunctionInfo(String name, MethodTypeDesc type, FuncType funcType, DefType defType) {}

	private record TableInfo(String fieldName, ClassDesc elementType, TableType tableType) {}

	private record GlobalInfo(String fieldName, ClassDesc containerType, ClassDesc elementType, GlobalType globalType) {}

	private record MemInfo(String fieldName, MemType memType) {}

	private record ElemInfo(String fieldName, ClassDesc fieldType, ClassDesc elementType, Elem elem) {}

	private record LocalInfo(int slotIndex, ClassDesc type) {}
}
