package dev.argon.jawawasm.engine.compiler;

import dev.argon.jawawasm.format.instructions.*;
import dev.argon.jawawasm.format.modules.*;
import dev.argon.jawawasm.format.modules.Module;
import dev.argon.jawawasm.format.types.AggregateType;
import dev.argon.jawawasm.format.types.DefType;
import dev.argon.jawawasm.format.types.FuncType;
import dev.argon.jawawasm.format.types.ResultType;

import java.lang.classfile.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;

import static dev.argon.jawawasm.engine.compiler.Constants.*;
import static dev.argon.jawawasm.engine.compiler.NameMangling.escapeName;
import static java.lang.constant.ConstantDescs.*;

public class ModuleClassGenerator extends WasmClassGenerator {
	ModuleClassGenerator(ModuleCompiler compiler, Module module, String className) {
		super(compiler);
		this.module = module;
		this.className = ClassDesc.of(compiler.getOptions().javaPackage(), className);
	}

	private final Module module;
	private final ClassDesc className;

	private final List<DefType> types = new ArrayList<>();
	private final List<ImportInfo> imports = new ArrayList<>();
	private final List<FunctionInfo> funcs = new ArrayList<>();
	private final List<TableInfo> tables = new ArrayList<>();
	private final List<GlobalInfo> globals = new ArrayList<>();
	private int memCount = 0;

	@Override
	public ClassDesc className() {
		return className;
	}

	@Override
	protected byte[] generateImpl() {
		return compiler.getOptions().classFile().build(className(), this::buildClass);
	}

	private void buildClass(ClassBuilder clb) {
		types.clear();
		imports.clear();
		funcs.clear();
		tables.clear();
		globals.clear();

		clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL);
		clb.withSuperclass(wasmModuleClass);

		for(var recType : module.types()) {
			for(int i = 0; i < recType.subtypes().size(); ++i) {
				types.add(new DefType(recType, i));
			}
		}

		for(var imp : module.imports()) {
			String localName;

			switch(imp.desc()) {
				case ImportDesc.Func func -> {
					localName = "func" + funcs.size();
					var defType = types.get(func.type().index());
					var type = compiler.getMethodType(defType);
					funcs.add(new FunctionInfo(localName, type, getFuncType(defType)));
					generateFunctionImport(clb, imp, func, localName, type);
				}
				case ImportDesc.Table table -> {
					localName = "table" + tables.size();
					var type = compiler.getTableType(table.type().elementType());
					tables.add(new TableInfo(localName, type));
					clb.withField(localName, type, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);
				}
				case ImportDesc.Global global -> {
					localName = "global" + globals.size();
					var type = compiler.getTableType(global.type().type());
					clb.withField(localName, type, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);
				}
				case ImportDesc.Mem _ -> {
					localName = "mem" + memCount;
					++memCount;
					clb.withField(localName, memoryType, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);
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
			var defType = types.get(func.type().index());
			var type = compiler.getMethodType(defType);
			funcs.add(new FunctionInfo(name, type, getFuncType(defType)));
			functionCodegens.add(() -> generateFunction(clb, name, type, func));
		}

		for(var elem : module.tables()) {
			throw new RuntimeException("Not implemented");
		}

		for(var elem : module.elems()) {
			throw new RuntimeException("Not implemented");
		}

		for(var data : module.datas()) {
			throw new RuntimeException("Not implemented");
		}

		clb.withMethodBody("<init>", MethodTypeDesc.ofDescriptor("()V"), ClassFile.ACC_PUBLIC, mb ->
			mb.aload(0)
				.invokespecial(wasmModuleClass, "<init>", MethodTypeDesc.ofDescriptor("()V"))
				.return_()
		);

		for(var funcCodegen : functionCodegens) {
			funcCodegen.run();
		}

		for(var export : module.exports()) {
			switch(export.desc()) {
				case ExportDesc.Func func -> {
					var funcInfo = funcs.get(func.func().index());
					var methodType = funcInfo.type();
					clb.withMethodBody(
						escapeName(export.name()),
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
				}
				case ExportDesc.Global global -> throw new RuntimeException("Not implemented");
				case ExportDesc.Mem mem -> throw new RuntimeException("Not implemented");
				case ExportDesc.Table table -> throw new RuntimeException("Not implemented");
				case ExportDesc.Tag tag -> throw new RuntimeException("Not implemented");
			}
		}
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
					case VOID -> throw new RuntimeException("Unexpected VOID type");
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

	private final class BytecodeGenerator {
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
				System.err.println("Stack: " + stackTypes);
				System.err.println("Instruction: " + insn);

				generateInstruction(insn);

				if(isUnreachable) {
					break;
				}
			}
		}


		private void generateInstruction(Instr insn) {
			switch(insn) {
				case ControlInstr controlInstr -> generateControlInstr(controlInstr);
				case MemoryInstr memoryInstr -> throw new RuntimeException("Not implemented");
				case NumericInstr numericInstr -> generateNumericInstr(numericInstr);
				case ParametricInstr parametricInstr -> generateParametricInstr(parametricInstr);
				case ReferenceInstr referenceInstr -> throw new RuntimeException("Not implemented");
				case TableInstr tableInstr -> throw new RuntimeException("Not implemented");
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
					var type = getBlockFuncType(blockType);

					var endLabel = cb.newLabel();

					var state = enterBlock(blockType);

					labels.add(new LabelInfo(endLabel, type.results()));
					generateInstructionBlock(new Expr(innerBlock));
					labels.removeLast();
					cb.labelBinding(endLabel);

					exitBlock(blockType, state);
					isUnreachable = false;
				}
				case ControlInstr.Loop(var blockType, var innerBlock) -> {
					var type = getBlockFuncType(blockType);

					var restartLoopLabel = cb.newLabel();

					var state = enterBlock(blockType);

					cb.labelBinding(restartLoopLabel);
					labels.add(new LabelInfo(restartLoopLabel, type.args()));
					generateInstructionBlock(new Expr(innerBlock));
					labels.removeLast();

					if(!isUnreachable) {
						exitBlock(blockType, state);
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

				case ControlInstr.If(var blockType, var thenBody, var elseBody) -> {
					var type = getBlockFuncType(blockType);

					var exitThenLabel = cb.newLabel();
					var elseLabel = cb.newLabel();
					var exitElseLabel = cb.newLabel();
					var endLabel = cb.newLabel();

					stackTypes.removeLast();
					cb.ifeq(elseLabel);

					var stackTypesCopy = new ArrayList<>(stackTypes);

					var state = enterBlock(blockType);
					labels.add(new LabelInfo(exitThenLabel, type.results()));
					generateInstructionBlock(new Expr(thenBody));
					labels.removeLast();
					cb.labelBinding(exitThenLabel);
					exitBlock(blockType, state);
					cb.goto_(endLabel);
					isUnreachable = false;

					cb.labelBinding(elseLabel);

					stackTypes.clear();
					stackTypes.addAll(stackTypesCopy);

					state = enterBlock(blockType);
					labels.add(new LabelInfo(exitElseLabel, type.results()));
					generateInstructionBlock(new Expr(elseBody));
					labels.removeLast();
					exitBlock(blockType, state);
					isUnreachable = false;

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

//				case ControlInstr.Br_OnCast brOnCast -> {
//				}
//				case ControlInstr.Br_OnCastFail brOnCastFail -> {
//				}
//				case ControlInstr.Br_OnNonNull brOnNonNull -> {
//				}
//				case ControlInstr.Br_OnNull brOnNull -> {
//				}
//				case ControlInstr.Br_Table brTable -> {
//				}
//				case ControlInstr.Call_Indirect callIndirect -> {
//				}
//				case ControlInstr.Call_Ref callRef -> {
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
				case VariableInstr.Global_Get globalGet -> throw new RuntimeException("Not implemented");
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

		private StashedBlockState enterBlock(ControlInstr.BlockType blockType) {
			var type = getBlockFuncType(blockType);

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

		private void exitBlock(ControlInstr.BlockType blockType, StashedBlockState state) {
			var type = getBlockFuncType(blockType);


			if(state.stashedStackValues.isEmpty()) {
				return;
			}

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

		private void doJump(Label label, ResultType labelType, Opcode jump, Opcode inverseJump) {
			if(stackTypes.size() == labelType.types().size()) {
				cb.branch(jump, label);
				return;
			}

			int oldTempVarSlot = tempVarSlot;
			var stashedValues = new ArrayList<StashedStackValue>();
			for(var resType : labelType.types()) {
				var javaType  = compiler.getValType(resType).type();
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
	}



	private void generateFunctionImport(ClassBuilder clb, Import imp, ImportDesc.Func func, String localName, MethodTypeDesc type) {
		clb.withMethodBody(localName, type, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL, cb -> {
			throw new RuntimeException("Not implemented");
		});
	}

	private FuncType getFuncType(DefType t) {
		var subtype = t.recursiveType().subtypes().get(t.index());
		return switch(subtype.compositeType()) {
			case AggregateType _ -> throw new RuntimeException("Unexpected aggregate type");
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

	private record ImportInfo(String module, String importedName, String localName, ImportDesc desc) {}

	private record FunctionInfo(String name, MethodTypeDesc type, FuncType funcType) {}

	private record TableInfo(String fieldName, ClassDesc type) {}

	private record GlobalInfo(String fieldName, ClassDesc type) {}

	private record Info(String fieldName, ClassDesc type, Elem elem) {}

	private record ElemInfo(String fieldName, ClassDesc type, Elem elem) {}

	private record LocalInfo(int slotIndex, ClassDesc type) {}
}
