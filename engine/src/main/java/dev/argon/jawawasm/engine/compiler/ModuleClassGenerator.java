package dev.argon.jawawasm.engine.compiler;

import dev.argon.jawawasm.format.instructions.*;
import dev.argon.jawawasm.format.modules.*;
import dev.argon.jawawasm.format.modules.Module;
import dev.argon.jawawasm.format.types.AggregateType;
import dev.argon.jawawasm.format.types.DefType;
import dev.argon.jawawasm.format.types.FuncType;
import dev.argon.jawawasm.format.types.ResultType;
import org.jspecify.annotations.Nullable;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;

import static dev.argon.jawawasm.engine.compiler.Constants.*;
import static dev.argon.jawawasm.engine.compiler.NameMangling.escapeName;
import static java.lang.constant.ConstantDescs.*;

public class ModuleClassGenerator extends WasmClassGenerator {
	ModuleClassGenerator(ModuleCompiler compiler, Module module, String className) {
		this.compiler = compiler;
		this.module = module;
		this.className = ClassDesc.of(compiler.getOptions().javaPackage(), className);
	}

	private final ModuleCompiler compiler;
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
		return ClassFile.of().build(className(), this::buildClass);
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
					var type = compiler.getMethodType(types.get(func.type().index()));
					funcs.add(new FunctionInfo(localName, type));
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
					clb.withField(localName, Constants.memoryType, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);
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
			var type = compiler.getMethodType(types.get(func.type().index()));
			funcs.add(new FunctionInfo(name, type));
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
		clb.withMethodBody(name, type, ClassFile.ACC_PRIVATE, cb -> {
			var funcType = types.get(func.type().index());

			var subtype = funcType.recursiveType().subtypes().get(funcType.index());
			var returnType = switch(subtype.compositeType()) {
				case AggregateType _ -> throw new RuntimeException("Unexpected aggregate type");
				case FuncType ft -> ft.results();
			};

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

			var bytecodeGen = new BytecodeGenerator(cb, locals);
			bytecodeGen.generateFunctionBody(func.body(), returnType);
		});
	}

	private final class BytecodeGenerator {
		public BytecodeGenerator(CodeBuilder cb, LocalInfo[] locals) {
			this.cb = cb;
			this.locals = locals;
		}


		private final CodeBuilder cb;
		private final LocalInfo[] locals;
		private boolean isUnreachable = false;

		public void generateFunctionBody(Expr body, ResultType resultType) {
			generateInstructionBlock(body, resultType);
		}

		public void generateInstructionBlock(Expr body) {
			generateInstructionBlock(body, null);
		}

		public void generateInstructionBlock(Expr body, @Nullable ResultType returnType) {
			for(var insn : body.body()) {
				generateInstruction(insn);

				if(isUnreachable) {
					isUnreachable = false;
					returnType = null; // No need to return the stack values when unreachable
					break;
				}
			}

			if(returnType != null) {
				generateReturn(returnType);
			}
		}

		private void generateInstruction(Instr insn) {
			switch(insn) {
				case ControlInstr controlInstr -> throw new RuntimeException("Not implemented");
				case MemoryInstr memoryInstr -> throw new RuntimeException("Not implemented");
				case NumericInstr numericInstr -> generateNumericInstr(numericInstr);
				case ParametricInstr parametricInstr -> throw new RuntimeException("Not implemented");
				case ReferenceInstr referenceInstr -> throw new RuntimeException("Not implemented");
				case TableInstr tableInstr -> throw new RuntimeException("Not implemented");
				case VariableInstr variableInstr -> generateVaiableInstr(variableInstr);
				case VectorInstr vectorInstr -> throw new RuntimeException("Not implemented");
			}
		}

		private void generateNumericInstr(NumericInstr instr) {
			switch(instr) {
				case NumericInstr.I32_Const(var value) -> cb.loadConstant(value);
				case NumericInstr.I64_Const(var value) -> cb.loadConstant(value);
				case NumericInstr.F32_Const(var value) -> cb.loadConstant(value);
				case NumericInstr.F64_Const(var value) -> cb.loadConstant(value);
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
				}
				case NumericInstr.Inn_ITestOp(var size, var op) -> {
					var descriptor = switch(size) {
						case _32 -> MethodTypeDesc.ofDescriptor("(I)Z");
						case _64 -> MethodTypeDesc.ofDescriptor("(J)Z");
					};

					switch(op) {
						case EQZ -> cb.invokestatic(utilClass, "equalsZero", descriptor);
					}
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
				case NumericInstr.I32_Wrap_I64() -> cb.l2i();
				case NumericInstr.I64_Extend_I32_S() -> cb.i2l();
				case NumericInstr.I64_Extend_I32_U() -> cb.invokestatic(CD_Integer, "toUnsignedLong", MethodTypeDesc.ofDescriptor("(I)J"));

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

		private void generateVaiableInstr(VariableInstr instr) {
			switch(instr) {
				case VariableInstr.Local_Get(var localIdx) -> {
					var localInfo = locals[localIdx.index()];
					cb.loadLocal(typeKind(localInfo.type()), localInfo.slotIndex());
				}
				case VariableInstr.Local_Set(var localIdx) -> {
					var localInfo = locals[localIdx.index()];
					cb.storeLocal(typeKind(localInfo.type()), localInfo.slotIndex());
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

		private void generateReturn(ResultType returnType) {
			var resultArgTypes = new ArrayList<ClassDesc>();

			for(var t : returnType.types()) {
				var realization = compiler.getValType(t);
				resultArgTypes.add(realization.type());
			}

			var returnTypeClass = compiler.getResultType(returnType);

			cb.invokestatic(compiler.getResultType(returnType), "of", MethodTypeDesc.of(returnTypeClass, resultArgTypes));
			cb.areturn();
		}
	}



	private void generateFunctionImport(ClassBuilder clb, Import imp, ImportDesc.Func func, String localName, MethodTypeDesc type) {
		clb.withMethodBody(localName, type, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL, cb -> {
			throw new RuntimeException("Not implemented");
		});
	}

	private record ImportInfo(String module, String importedName, String localName, ImportDesc desc) {}

	private record FunctionInfo(String name, MethodTypeDesc type) {}

	private record TableInfo(String fieldName, ClassDesc type) {}

	private record GlobalInfo(String fieldName, ClassDesc type) {}

	private record Info(String fieldName, ClassDesc type, Elem elem) {}

	private record ElemInfo(String fieldName, ClassDesc type, Elem elem) {}

	private record LocalInfo(int slotIndex, ClassDesc type) {}
}
