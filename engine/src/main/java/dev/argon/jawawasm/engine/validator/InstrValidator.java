package dev.argon.jawawasm.engine.validator;

import dev.argon.jawawasm.format.instructions.*;
import dev.argon.jawawasm.format.modules.TableIdx;
import dev.argon.jawawasm.format.modules.TypeIdx;
import dev.argon.jawawasm.format.types.*;
import dev.argon.jawawasm.format.modules.LabelIdx;
import dev.argon.jawawasm.format.modules.MemIdx;

import java.util.ArrayList;
import java.util.List;

class InstrValidator extends ValidatorBase {
	public InstrValidator(Context context) {
		super(context);
		tv = new TypeValidator(context);
		subtyping = new Subtyping(context);
	}

	private final TypeValidator tv;
	private final Subtyping subtyping;
	
	public void validateExpr(Expr expr, ResultType resultType) throws ValidationException {
		validateInstructions(expr.body(), new ResultType(List.of()), resultType);
	}

	public void validateInstructions(List<? extends Instr> instrs, ResultType argType, ResultType resultType) throws ValidationException {
		List<ValType> stack = new ArrayList<>(argType.types());

		var sv = new StackValidator(stack);
		for(Instr instr : instrs) {
			sv.validateInstr(instr);
		}

		sv.pop(resultType);
		require(stack.isEmpty(), "type mismatch");
	}

	public void requireConstantExpr(Expr expr) throws ValidationException {
		for(var instr : expr.body()) {
			requireConstantInstr(instr);
		}
	}

	public void requireConstantInstr(Instr instr) throws ValidationException {
		switch(instr) {
			case NumericInstr.I32_Const(_) -> {}
			case NumericInstr.I64_Const(_) -> {}
			case NumericInstr.F32_Const(_) -> {}
			case NumericInstr.F64_Const(_) -> {}
			case NumericInstr.Inn_IBinOp(_, var op)
				when op == NumericInstr.IBinOp.ADD
					|| op == NumericInstr.IBinOp.SUB
					|| op == NumericInstr.IBinOp.MUL -> {}
			case VectorInstr.V128_Const(_) -> {}
			case ReferenceInstr.Ref_Null(_) -> {}
			case ReferenceInstr.Ref_I31() -> {}
			case ReferenceInstr.Ref_Func(_) -> {}
			case ReferenceInstr.Struct_New(_), ReferenceInstr.Struct_New_Default(_) -> {}
			case ReferenceInstr.Array_New(_), ReferenceInstr.Array_New_Default(_), ReferenceInstr.Array_New_Fixed _ -> {}
			case ReferenceInstr.Any_Convert_Extern(), ReferenceInstr.Extern_Convert_Any() -> {}
			case VariableInstr.Global_Get(var global) -> {
				context.requireGlobal(global);
				require(context.getGlobal(global).mutability() == Mut.Const, "constant expression required");
			}
			default -> throw new ValidationException("constant expression required");
		}
	}

	public RefType requireRefType(ValType valType) throws ValidationException {
		if(valType instanceof BotType botType) {
			return new RefType(false, botType);
		}
		else if(valType instanceof RefType refType) {
			return refType;
		}
		else {
			throw new ValidationException("type mismatch");
		}
	}

	public void requireFuncType(HeapType heapType) throws ValidationException {
		if(heapType instanceof TypeIdx funcType) {
			context.requireFuncType(funcType);
			return;
		}

		if(heapType instanceof BotType || heapType == HeapType.AbstractHeapType.FUNC) {
			return;
		}

		if(heapType instanceof DefType defType) {
			var subType = defType.recursiveType().subtypes().get(defType.index());
			if(subType.compositeType() instanceof FuncType) {
				return;
			}
		}

		throw new ValidationException("type mismatch");
	}

	private final class StackValidator {
		public StackValidator(List<ValType> stack) {
			this.stack = stack;

		}

		private final List<ValType> stack;
		private boolean unreachable = false;

		private void push(ValType t) {
			stack.add(t);
		}

		private void push(ResultType t) {
			for(var t2 : t.types()) {
				push(t2);
			}
		}

		private void pushAddress(MemIdx memIdx) throws ValidationException {
			push(AddressTypeUtils.asNumType(context.getMem(memIdx).addrType()));
		}

		private void pushIndex(TableIdx tableIdx) throws ValidationException {
			push(AddressTypeUtils.asNumType(context.getTable(tableIdx).addrType()));
		}

		private ValType pop() throws ValidationException {
			if(stack.isEmpty()) {
				if(unreachable) {
					return new BotType();
				}
				else {
					throw new ValidationException("type mismatch due to empty stack");
				}
			}

			return stack.removeLast();
		}

		private void pop(ValType t) throws ValidationException {
			var t2 = pop();
			if(t2 != null && !subtyping.isSubtypeVal(t2, t)) {
				throw new ValidationException("type mismatch expected: " + t + ", actual: " + t2);
			}
		}

		private void pop(ResultType t) throws ValidationException {
			for(int i = t.types().size() - 1; i >= 0; --i) {
				pop(t.types().get(i));
			}
		}

		private void popAddress(MemIdx memIdx) throws ValidationException {
			pop(AddressTypeUtils.asNumType(context.getMem(memIdx).addrType()));
		}

		private void popAddress(MemIdx memIdx1, MemIdx memIdx2) throws ValidationException {
			switch(context.getMem(memIdx1).addrType()) {
				case I32 -> pop(NumType.I32);
				case I64 -> popAddress(memIdx2);
			}
		}

		private void popIndex(TableIdx tableIdx) throws ValidationException {
			pop(AddressTypeUtils.asNumType(context.getTable(tableIdx).addrType()));
		}

		private void popIndex(TableIdx tableIdx1, TableIdx tableIdx2) throws ValidationException {
			switch(context.getTable(tableIdx1).addrType()) {
				case I32 -> pop(NumType.I32);
				case I64 -> popIndex(tableIdx2);
			}
		}


		public void validateInstr(Instr instr) throws ValidationException {
			switch(instr) {
				case NumericInstr numericInstr -> validateNumericInstr(numericInstr);
				case ReferenceInstr referenceInstr -> validateReferenceInstr(referenceInstr);
				case VectorInstr vectorInstr -> validateVectorInstr(vectorInstr);
				case ParametricInstr parametricInstr -> validateParametricInstr(parametricInstr);
				case VariableInstr variableInstr -> validateVariableInstr(variableInstr);
				case TableInstr tableInstr -> validateTableInstr(tableInstr);
				case MemoryInstr memoryInstr -> validateMemoryInstr(memoryInstr);
				case ControlInstr controlInstr -> validateControlInstr(controlInstr);
			}
		}

		private void validateNumericInstr(NumericInstr instr) throws ValidationException {
			switch(instr) {
				case NumericInstr.I32_Const(_) -> push(NumType.I32);
				case NumericInstr.I64_Const(_) -> push(NumType.I64);
				case NumericInstr.F32_Const(_) -> push(NumType.F32);
				case NumericInstr.F64_Const(_) -> push(NumType.F64);
				case NumericInstr.Inn_IUnOp(var numSize, _) -> {
					var t = intTypeForSize(numSize);
					pop(t);
					push(t);
				}

				case NumericInstr.Fnn_FUnOp(var numSize, _) -> {
					var t = floatTypeForSize(numSize);
					pop(t);
					push(t);
				}

				case NumericInstr.Inn_IBinOp(var numSize, _) -> {
					var t = intTypeForSize(numSize);
					pop(t);
					pop(t);
					push(t);
				}

				case NumericInstr.Fnn_FBinOp(var numSize, _) -> {
					var t = floatTypeForSize(numSize);
					pop(t);
					pop(t);
					push(t);
				}

				case NumericInstr.Inn_ITestOp(var numSize, _) -> {
					var t = intTypeForSize(numSize);
					pop(t);
					push(NumType.I32);
				}

				case NumericInstr.Inn_IRelOp(var numSize, _) -> {
					var t = intTypeForSize(numSize);
					pop(t);
					pop(t);
					push(NumType.I32);
				}

				case NumericInstr.Fnn_FRelOp(var numSize, _) -> {
					var t = floatTypeForSize(numSize);
					pop(t);
					pop(t);
					push(NumType.I32);
				}

				case NumericInstr.Inn_Extend8_S(var numSize) -> {
					var t = intTypeForSize(numSize);
					pop(t);
					push(t);
				}

				case NumericInstr.Inn_Extend16_S(var numSize) -> {
					var t = intTypeForSize(numSize);
					pop(t);
					push(t);
				}

				case NumericInstr.I64_Extend32_S() -> {
					pop(NumType.I64);
					push(NumType.I64);
				}

				case NumericInstr.I32_Wrap_I64() -> {
					pop(NumType.I64);
					push(NumType.I32);
				}

				case NumericInstr.I64_Extend_I32_S(), NumericInstr.I64_Extend_I32_U() -> {
					pop(NumType.I32);
					push(NumType.I64);
				}

				case NumericInstr.Inn_Trunc_Fmm_S(var intSize, var floatSize) -> {
					pop(floatTypeForSize(floatSize));
					push(intTypeForSize(intSize));
				}

				case NumericInstr.Inn_Trunc_Fmm_U(var intSize, var floatSize) -> {
					pop(floatTypeForSize(floatSize));
					push(intTypeForSize(intSize));
				}

				case NumericInstr.Inn_Trunc_Sat_Fmm_S(var intSize, var floatSize) -> {
					pop(floatTypeForSize(floatSize));
					push(intTypeForSize(intSize));
				}

				case NumericInstr.Inn_Trunc_Sat_Fmm_U(var intSize, var floatSize) -> {
					pop(floatTypeForSize(floatSize));
					push(intTypeForSize(intSize));
				}

				case NumericInstr.F32_Demote_F64() -> {
					pop(NumType.F64);
					push(NumType.F32);
				}

				case NumericInstr.F64_Promote_F32() -> {
					pop(NumType.F32);
					push(NumType.F64);
				}

				case NumericInstr.Fnn_Convert_Imm_S(var floatSize, var intSize) -> {
					pop(intTypeForSize(intSize));
					push(floatTypeForSize(floatSize));
				}

				case NumericInstr.Fnn_Convert_Imm_U(var floatSize, var intSize) -> {
					pop(intTypeForSize(intSize));
					push(floatTypeForSize(floatSize));
				}

				case NumericInstr.Inn_Reinterpret_Fnn(var numSize) -> {
					pop(floatTypeForSize(numSize));
					push(intTypeForSize(numSize));
				}

				case NumericInstr.Fnn_Reinterpret_Inn(var numSize) -> {
					pop(intTypeForSize(numSize));
					push(floatTypeForSize(numSize));
				}
			};
		}

		private ValType intTypeForSize(NumericInstr.NumSize size) throws ValidationException {
			return switch(size) {
				case _32 -> NumType.I32;
				case _64 -> NumType.I64;
			};
		}

		private ValType floatTypeForSize(NumericInstr.NumSize size) throws ValidationException {
			return switch(size) {
				case _32 -> NumType.F32;
				case _64 -> NumType.F64;
			};
		}

		private void validateReferenceInstr(ReferenceInstr instr) throws ValidationException {
			switch(instr) {
				case ReferenceInstr.Ref_Null(var t) -> {
					tv.validateHeapType(t);
					push(new RefType(true, t));
				}
				case ReferenceInstr.Ref_IsNull() -> {
					requireRefType(pop());
					push(NumType.I32);
				}
				case ReferenceInstr.Ref_Func(var funcIdx) -> {
					context.requireRef(funcIdx);
					context.requireFunc(funcIdx);

					push(new RefType(false, context.getFunc(funcIdx)));
				}
				case ReferenceInstr.Ref_Eq() -> {
					pop(new RefType(true, HeapType.AbstractHeapType.EQ));
					pop(new RefType(true, HeapType.AbstractHeapType.EQ));
					push(NumType.I32);
				}
				case ReferenceInstr.Ref_AsNonNull() -> {
					var refType = requireRefType(pop());
					push(new RefType(false, refType.heapType()));
				}

				case ReferenceInstr.Ref_Test(var rt) -> {
					tv.validateReferenceType(rt);
					var t2 = requireRefType(pop());
					var t2b = new RefType(t2.isNullable(), toTopType(t2.heapType()));
					require(subtyping.isSubtypeVal(rt, t2b), "invalid test type: Expected " + t2b + ", Actual " + rt);
					push(NumType.I32);
				}

				case ReferenceInstr.Ref_Cast(var rt) -> {
					tv.validateReferenceType(rt);
					var t2 = pop();
					require(subtyping.isSubtypeVal(rt, t2), "invalid test type");
					push(rt);
				}

				case ReferenceInstr.Ref_I31() -> {
					pop(NumType.I32);
					push(new RefType(false, HeapType.AbstractHeapType.I31));
				}

				case ReferenceInstr.I31_Get_S(), ReferenceInstr.I31_Get_U() -> {
					pop(new RefType(true, HeapType.AbstractHeapType.I31));
					push(NumType.I32);
				}

				case ReferenceInstr.Struct_New(var typeIdx) -> {
					context.requireStructType(typeIdx);
					var struct = (StructType)context.getCompositeType(typeIdx);

					for(var field : struct.fields().reversed()) {
						pop(unpack(field.storageType()));
					}

					push(new RefType(false, context.getType(typeIdx)));
				}

				case ReferenceInstr.Struct_New_Default(var typeIdx) -> {
					context.requireStructType(typeIdx);
					var struct = (StructType)context.getCompositeType(typeIdx);

					for(var field : struct.fields().reversed()) {
						require(context.isDefaultable(field.storageType()), "field must be defaultable");
					}

					push(new RefType(false, context.getType(typeIdx)));
				}

				case ReferenceInstr.Struct_Get(var typeIdx, var fieldIdx) -> {
					context.requireStructType(typeIdx);
					var struct = (StructType)context.getCompositeType(typeIdx);
					var fieldType = struct.fields().get(fieldIdx.index());

					var t = switch(fieldType.storageType()) {
						case PackedType _ -> throw new ValidationException("Unexpected packed type");
						case ValType valType -> valType;
					};

					var refType = new RefType(true, context.getType(typeIdx));

					pop(refType);
					push(t);
				}

				case ReferenceInstr.Struct_Get_S(var typeIdx, var fieldIdx) -> {
					context.requireStructType(typeIdx);
					var struct = (StructType)context.getCompositeType(typeIdx);
					var fieldType = struct.fields().get(fieldIdx.index());

					if(!(fieldType.storageType() instanceof PackedType)) {
						throw new ValidationException("Unexpected val type");
					}

					var refType = new RefType(true, context.getType(typeIdx));

					pop(refType);
					push(NumType.I32);
				}

				case ReferenceInstr.Struct_Get_U(var typeIdx, var fieldIdx) -> {
					context.requireStructType(typeIdx);
					var struct = (StructType)context.getCompositeType(typeIdx);
					var fieldType = struct.fields().get(fieldIdx.index());

					if(!(fieldType.storageType() instanceof PackedType)) {
						throw new ValidationException("Unexpected val type");
					}

					var refType = new RefType(true, context.getType(typeIdx));

					pop(refType);
					push(NumType.I32);
				}

				case ReferenceInstr.Struct_Set(var typeIdx, var fieldIdx) -> {
					context.requireStructType(typeIdx);
					var struct = (StructType)context.getCompositeType(typeIdx);
					var fieldType = struct.fields().get(fieldIdx.index());

					require(fieldType.mut() == Mut.Var, "field is immutable");

					var refType = new RefType(true, context.getType(typeIdx));

					pop(unpack(fieldType.storageType()));
					pop(refType);
				}

				case ReferenceInstr.Array_New(var typeIdx) -> {
					context.requireArrayType(typeIdx);
					var array = (ArrayType)context.getCompositeType(typeIdx);

					pop(NumType.I32);
					pop(unpack(array.fieldType().storageType()));
					push(new RefType(false, context.getType(typeIdx)));
				}

				case ReferenceInstr.Array_New_Default(var typeIdx) -> {
					context.requireArrayType(typeIdx);
					var array = (ArrayType)context.getCompositeType(typeIdx);
					require(context.isDefaultable(array.fieldType().storageType()), "array field must be defaultable");

					pop(NumType.I32);
					push(new RefType(false, context.getType(typeIdx)));
				}

				case ReferenceInstr.Array_New_Fixed(var typeIdx, int n) -> {
					context.requireArrayType(typeIdx);
					var array = (ArrayType)context.getCompositeType(typeIdx);

					for(int i = 0; i < n; ++i) {
						pop(unpack(array.fieldType().storageType()));
					}

					push(new RefType(false, context.getType(typeIdx)));
				}

				case ReferenceInstr.Array_New_Data(var typeIdx, var dataIdx) -> {
					context.requireArrayType(typeIdx);
					var array = (ArrayType)context.getCompositeType(typeIdx);

					if(array.fieldType().storageType() instanceof RefType) {
						throw new ValidationException("type mismatch");
					}

					context.requireData(dataIdx);

					pop(NumType.I32);
					pop(NumType.I32);
					push(new RefType(false, context.getType(typeIdx)));
				}

				case ReferenceInstr.Array_New_Elem(var typeIdx, var elemIdx) -> {
					context.requireArrayType(typeIdx);
					var array = (ArrayType)context.getCompositeType(typeIdx);

					if(!(array.fieldType().storageType() instanceof RefType arrayElemType)) {
						throw new ValidationException("type mismatch");
					}

					context.requireElem(elemIdx);
					var elemType = context.getElem(elemIdx);

					require(subtyping.isSubtypeRef(elemType, arrayElemType), "type mismatch");

					pop(NumType.I32);
					pop(NumType.I32);
					push(new RefType(false, context.getType(typeIdx)));
				}

				case ReferenceInstr.Array_Get(var typeIdx) -> {
					context.requireArrayType(typeIdx);
					var array = (ArrayType)context.getCompositeType(typeIdx);

					var t = switch(array.fieldType().storageType()) {
						case PackedType _ -> throw new ValidationException("Unexpected packed type");
						case ValType valType -> valType;
					};

					var refType = new RefType(true, context.getType(typeIdx));

					pop(NumType.I32);
					pop(refType);
					push(t);
				}

				case ReferenceInstr.Array_Get_S(var typeIdx) -> {
					context.requireArrayType(typeIdx);
					var array = (ArrayType)context.getCompositeType(typeIdx);

					if(!(array.fieldType().storageType() instanceof PackedType)) {
						throw new ValidationException("Unexpected val type");
					}

					var refType = new RefType(true, context.getType(typeIdx));

					pop(NumType.I32);
					pop(refType);
					push(NumType.I32);
				}

				case ReferenceInstr.Array_Get_U(var typeIdx) -> {
					context.requireArrayType(typeIdx);
					var array = (ArrayType)context.getCompositeType(typeIdx);

					if(!(array.fieldType().storageType() instanceof PackedType)) {
						throw new ValidationException("Unexpected val type");
					}

					var refType = new RefType(true, context.getType(typeIdx));

					pop(NumType.I32);
					pop(refType);
					push(NumType.I32);
				}

				case ReferenceInstr.Array_Set(var typeIdx) -> {
					context.requireArrayType(typeIdx);
					var array = (ArrayType)context.getCompositeType(typeIdx);

					require(array.fieldType().mut() == Mut.Var, "array is immutable");

					var refType = new RefType(true, context.getType(typeIdx));

					pop(unpack(array.fieldType().storageType()));
					pop(NumType.I32);
					pop(refType);
				}

				case ReferenceInstr.Array_Len() -> {
					pop(new RefType(true, HeapType.AbstractHeapType.ARRAY));
					push(NumType.I32);
				}

				case ReferenceInstr.Array_Fill(var typeIdx) -> {
					context.requireArrayType(typeIdx);
					var array = (ArrayType)context.getCompositeType(typeIdx);

					require(array.fieldType().mut() == Mut.Var, "array is immutable");

					pop(NumType.I32);
					pop(unpack(array.fieldType().storageType()));
					pop(NumType.I32);
					pop(new RefType(true, context.getType(typeIdx)));
				}

				case ReferenceInstr.Array_Copy(var destType, var srcType) -> {
					context.requireArrayType(destType);
					context.requireArrayType(srcType);
					var dest = (ArrayType)context.getCompositeType(destType);
					var src = (ArrayType)context.getCompositeType(srcType);

					require(dest.fieldType().mut() == Mut.Var, "array is immutable");

					require(subtyping.isSubtypeStorage(src.fieldType().storageType(), dest.fieldType().storageType()), "array types do not match");

					pop(NumType.I32);
					pop(NumType.I32);
					pop(new RefType(true, context.getType(srcType)));
					pop(NumType.I32);
					pop(new RefType(true, context.getType(destType)));
				}

				case ReferenceInstr.Array_Init_Data(var typeIdx, var dataIdx) -> {
					context.requireArrayType(typeIdx);
					var array = (ArrayType)context.getCompositeType(typeIdx);

					require(array.fieldType().mut() == Mut.Var, "array is immutable");

					if(array.fieldType().storageType() instanceof RefType) {
						throw new ValidationException("array type is not numeric or vector");
					}

					context.requireData(dataIdx);

					pop(NumType.I32);
					pop(NumType.I32);
					pop(NumType.I32);
					pop(new RefType(true, context.getType(typeIdx)));
				}

				case ReferenceInstr.Array_Init_Elem(var typeIdx, var elemIdx) -> {
					context.requireArrayType(typeIdx);
					var array = (ArrayType)context.getCompositeType(typeIdx);

					require(array.fieldType().mut() == Mut.Var, "array is immutable");

					if(!(array.fieldType().storageType() instanceof RefType arrayElemType)) {
						throw new ValidationException("type mismatch");
					}

					context.requireElem(elemIdx);
					var elemType = context.getElem(elemIdx);

					require(subtyping.isSubtypeRef(elemType, arrayElemType), "type mismatch");

					pop(NumType.I32);
					pop(NumType.I32);
					pop(NumType.I32);
					pop(new RefType(true, context.getType(typeIdx)));
				}

				case ReferenceInstr.Any_Convert_Extern() -> {
					var t = pop();

					boolean isNull = switch(t) {
						case RefType(boolean isNullable, HeapType heapType) -> {
							require(subtyping.isSubtypeHeap(heapType, HeapType.AbstractHeapType.EXTERN), "type mismatch");
							yield isNullable;
						}
						case BotType() -> false;
						default -> throw new ValidationException("type mismatch");
					};

					push(new RefType(isNull, HeapType.AbstractHeapType.ANY));
				}

				case ReferenceInstr.Extern_Convert_Any() -> {
					var t = pop();

					boolean isNull = switch(t) {
						case RefType(boolean isNullable, HeapType heapType) -> {
							require(subtyping.isSubtypeHeap(heapType, HeapType.AbstractHeapType.ANY), "type mismatch");
							yield isNullable;
						}
						case BotType() -> false;
						default -> throw new ValidationException("type mismatch");
					};

					push(new RefType(isNull, HeapType.AbstractHeapType.EXTERN));
				}

				default -> throw new RuntimeException("Not implemented: " + instr);
			}
		}

		private ValType unpack(StorageType t) {
			return switch(t) {
				case PackedType _ -> NumType.I32;
				case ValType valType -> valType;
			};
		}

		private HeapType toTopType(HeapType t) {
			return switch(t) {
				case TypeIdx typeIdx -> toTopType(context.getType(typeIdx));
				case BotType _ -> t;
				case DefType defType -> switch(defType.recursiveType().subtypes().get(defType.index()).compositeType()) {
					case FuncType _ -> HeapType.AbstractHeapType.FUNC;
					case StructType _ -> HeapType.AbstractHeapType.STRUCT;
					case ArrayType _ -> HeapType.AbstractHeapType.ARRAY;
				};
				case HeapType.AbstractHeapType at -> switch(at) {
					case NONE -> HeapType.AbstractHeapType.ANY;
					case NOEXN -> HeapType.AbstractHeapType.EXN;
					case NOFUNC -> HeapType.AbstractHeapType.FUNC;
					case NOEXTERN -> HeapType.AbstractHeapType.EXTERN;
					default -> t;
				};
				case RecTypeIdx _ -> t;
			};
		}

		private void validateVectorInstr(VectorInstr instr) throws ValidationException {
			switch(instr) {
				case VectorInstr.V128_Const(var v) -> push(VecType.V128);

				case VectorInstr.VVUnOp _ -> {
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.VVBinOp _ -> {
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.VVTernOp _ -> {
					pop(VecType.V128);
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.VVTestOp _ -> {
					pop(VecType.V128);
					push(NumType.I32);
				}


				case VectorInstr.I16x8_Relaxed_Dot_I8x16_I7x16_S _ -> {
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.I32x4_Relaxed_Dot_I8x16_I7x16_Add_S _ -> {
					pop(VecType.V128);
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.I8x16_Op_Instr(var op) -> validateVectorInstrOp(op, NumType.I32, 16);
				case VectorInstr.I16x8_Op_Instr(var op) -> validateVectorInstrOp(op, NumType.I32, 8);
				case VectorInstr.I32x4_Op_Instr(var op) -> validateVectorInstrOp(op, NumType.I32, 4);
				case VectorInstr.I64x2_Op_Instr(var op) -> validateVectorInstrOp(op, NumType.I64, 2);
				case VectorInstr.F32x4_Op_Instr(var op) -> validateVectorInstrOp(op, NumType.F32, 4);
				case VectorInstr.F64x2_Op_Instr(var op) -> validateVectorInstrOp(op, NumType.F64, 2);
				case VectorInstr.F32x4_Ternary_Op_Instr(var op) -> validateVectorInstrOp(op, NumType.F32, 4);
				case VectorInstr.F64x2_Ternary_Op_Instr(var op) -> validateVectorInstrOp(op, NumType.F64, 2);
			}
		}

		private void validateVectorInstrOp(VectorInstr.AnyOp op, ValType scalarType, int shapeDim) throws ValidationException {
			switch(op) {
				case VectorInstr.Swizzle() -> {
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.Shuffle(var laneIndexes) -> {
					for(int i = 0; i < 16; ++i) {
						require(Byte.toUnsignedInt(laneIndexes.extractLane8(i)) < 32, "invalid lane index");
					}
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.Splat() -> {
					pop(scalarType);
					push(VecType.V128);
				}

				case VectorInstr.ExtractLane(var laneIdx) -> {
					require(Byte.toUnsignedInt(laneIdx) < shapeDim, "invalid lane index");
					pop(VecType.V128);
					push(scalarType);
				}

				case VectorInstr.ExtractLane_S(var laneIdx) -> {
					require(Byte.toUnsignedInt(laneIdx) < shapeDim, "invalid lane index");
					pop(VecType.V128);
					push(scalarType);
				}

				case VectorInstr.ExtractLane_U(var laneIdx) -> {
					require(Byte.toUnsignedInt(laneIdx) < shapeDim, "invalid lane index");
					pop(VecType.V128);
					push(scalarType);
				}

				case VectorInstr.ReplaceLane(var laneIdx) -> {
					require(Byte.toUnsignedInt(laneIdx) < shapeDim, "invalid lane index");
					pop(scalarType);
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.VNUnOp _ -> {
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.VNBinOp _ -> {
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.VNRelOp _ -> {
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.VIShiftOp _ -> {
					pop(NumType.I32);
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.All_True() -> {
					pop(VecType.V128);
					push(NumType.I32);
				}

				case VectorInstr.VCVTop_HalfQ_Shape_ZQ _ -> {
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.Narrow_Shape _ -> {
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.BitMask() -> {
					pop(VecType.V128);
					push(NumType.I32);
				}

				case VectorInstr.Dot_I16x8_S() -> {
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.ExtMul _ -> {
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.ExtAdd _ -> {
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.Q15mulr_Sat_S() -> {
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}
				case VectorInstr.VIAverageOps _ -> {
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}
				case VectorInstr.VIMinMaxOp _ -> {
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}
				case VectorInstr.VIMulOp _ -> {
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}
				case VectorInstr.VISatBinOp _ -> {
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}
				case VectorInstr.Popcnt() -> {
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.F32x4_Ternary_Op _, VectorInstr.F64x2_Ternary_Op _ -> {
					pop(VecType.V128);
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}
			}
		}

		private void validateParametricInstr(ParametricInstr instr) throws ValidationException {
			switch(instr) {
				case ParametricInstr.Drop() -> {
					pop();
				}
				case ParametricInstr.Select(var types) -> {
					if(types == null) {
						pop(NumType.I32);
						var t = pop();
						switch(t) {
							case BotType() -> t = pop();
							case NumType _, VecType _ -> {
								pop(t);
							}
							default -> throw new ValidationException("type mismatch");
						}
						push(t);
					}
					else {
						require(types.size() == 1, "invalid result arity");

						for(var t : types) {
							tv.validateValType(t);
						}

						pop(NumType.I32);
						pop(new ResultType(types));
						pop(new ResultType(types));
						push(new ResultType(types));
					}
				}
			}
		}

		private void validateVariableInstr(VariableInstr instr) throws ValidationException {
			switch(instr) {
				case VariableInstr.Local_Get(var local) -> {
					context.requireLocal(local);
					context.requireInitLocal(local);
					push(context.getLocal(local));
				}
				case VariableInstr.Local_Set(var local) -> {
					context.requireLocal(local);
					context.initializeLocal(local);
					pop(context.getLocal(local));
				}
				case VariableInstr.Local_Tee(var local) -> {
					context.requireLocal(local);
					context.initializeLocal(local);
					pop(context.getLocal(local));
					push(context.getLocal(local));
				}

				case VariableInstr.Global_Get(var global) -> {
					context.requireGlobal(global);
					push(context.getGlobal(global).type());
				}
				case VariableInstr.Global_Set(var global) -> {
					context.requireGlobal(global);
					require(context.getGlobal(global).mutability() == Mut.Var, "immutable global");
					pop(context.getGlobal(global).type());
				}
			}
		}

		private void validateTableInstr(TableInstr instr) throws ValidationException {
			switch(instr) {
				case TableInstr.Table_Get(var tableIdx) -> {
					context.requireTable(tableIdx);
					var t = context.getTable(tableIdx);
					popIndex(tableIdx);
					push(t.elementType());
				}

				case TableInstr.Table_Set(var tableIdx) -> {
					context.requireTable(tableIdx);
					var t = context.getTable(tableIdx);
					pop(t.elementType());
					popIndex(tableIdx);
				}

				case TableInstr.Table_Size(var tableIdx) -> {
					context.requireTable(tableIdx);
					pushIndex(tableIdx);
				}

				case TableInstr.Table_Grow(var tableIdx) -> {
					context.requireTable(tableIdx);
					var t = context.getTable(tableIdx);
					popIndex(tableIdx);
					pop(t.elementType());
					pushIndex(tableIdx);
				}

				case TableInstr.Table_Fill(var tableIdx) -> {
					context.requireTable(tableIdx);
					var t = context.getTable(tableIdx);
					popIndex(tableIdx);
					pop(t.elementType());
					popIndex(tableIdx);
				}

				case TableInstr.Table_Copy(var dest, var src) -> {
					context.requireTable(dest);
					var t1 = context.getTable(dest);
					context.requireTable(src);
					var t2 = context.getTable(src);
					require(subtyping.isSubtypeRef(t2.elementType(), t1.elementType()), "type mismatch");

					popIndex(dest, src);
					popIndex(src);
					popIndex(dest);
				}

				case TableInstr.Table_Init(var tableIdx, var elemIdx) -> {
					context.requireTable(tableIdx);
					var t1 = context.getTable(tableIdx);
					context.requireElem(elemIdx);
					var t2 = context.getElem(elemIdx);
					require(subtyping.isSubtypeRef(t2, t1.elementType()), "type mismatch");

					pop(NumType.I32);
					pop(NumType.I32);
					popIndex(tableIdx);
				}

				case TableInstr.Elem_Drop(var elemIdx) -> {
					context.requireElem(elemIdx);
				}
			}
		}

		private void validateMemoryInstr(MemoryInstr instr) throws ValidationException {
			switch(instr) {
				case MemoryInstr.Inn_Load(var numSize, var memArg) -> {
					context.requireMem(memArg.memIdx());
					checkMemArg(numSize, memArg);
					popAddress(memArg.memIdx());
					push(intTypeForSize(numSize));
				}

				case MemoryInstr.Fnn_Load(var numSize, var memArg) -> {
					context.requireMem(memArg.memIdx());
					checkMemArg(numSize, memArg);
					popAddress(memArg.memIdx());
					push(floatTypeForSize(numSize));
				}

				case MemoryInstr.Inn_Store(var numSize, var memArg) -> {
					context.requireMem(memArg.memIdx());
					checkMemArg(numSize, memArg);
					pop(intTypeForSize(numSize));
					popAddress(memArg.memIdx());
				}

				case MemoryInstr.Fnn_Store(var numSize, var memArg) -> {
					context.requireMem(memArg.memIdx());
					checkMemArg(numSize, memArg);
					pop(floatTypeForSize(numSize));
					popAddress(memArg.memIdx());
				}

				case MemoryInstr.V128_Load(var memArg) -> {
					context.requireMem(memArg.memIdx());

					checkVectorAlignment(memArg);
					popAddress(memArg.memIdx());
					push(VecType.V128);
				}

				case MemoryInstr.V128_Store(var memArg) -> {
					context.requireMem(memArg.memIdx());
					checkVectorAlignment(memArg);
					pop(VecType.V128);
					popAddress(memArg.memIdx());
				}

				case MemoryInstr.Inn_Load8_U(var numSize, var memArg) -> {
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 0) <= 0, "alignment must not be larger than natural");
					popAddress(memArg.memIdx());
					push(intTypeForSize(numSize));
				}

				case MemoryInstr.Inn_Load8_S(var numSize, var memArg) -> {
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 0) <= 0, "alignment must not be larger than natural");
					popAddress(memArg.memIdx());
					push(intTypeForSize(numSize));
				}

				case MemoryInstr.Inn_Store8(var numSize, var memArg) -> {
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 0) <= 0, "alignment must not be larger than natural");
					pop(intTypeForSize(numSize));
					popAddress(memArg.memIdx());
				}
				case MemoryInstr.Inn_Load16_S(var numSize, var memArg) -> {
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 1) <= 0, "alignment must not be larger than natural");
					popAddress(memArg.memIdx());
					push(intTypeForSize(numSize));
				}
				case MemoryInstr.Inn_Load16_U(var numSize, var memArg) -> {
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 1) <= 0, "alignment must not be larger than natural");
					popAddress(memArg.memIdx());
					push(intTypeForSize(numSize));
				}
				case MemoryInstr.Inn_Store16(var numSize, var memArg) -> {
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 1) <= 0, "alignment must not be larger than natural");
					pop(intTypeForSize(numSize));
					popAddress(memArg.memIdx());
				}

				case MemoryInstr.I64_Load32_S(var memArg) -> {
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 2) <= 0, "alignment must not be larger than natural");
					popAddress(memArg.memIdx());
					push(NumType.I64);
				}

				case MemoryInstr.I64_Load32_U(var memArg) -> {
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 2) <= 0, "alignment must not be larger than natural");
					popAddress(memArg.memIdx());
					push(NumType.I64);
				}

				case MemoryInstr.I64_Store32(var memArg) -> {
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 2) <= 0, "alignment must not be larger than natural");
					pop(NumType.I64);
					popAddress(memArg.memIdx());
				}

				case MemoryInstr.V128_Load8x8_S(var memArg) -> {
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 3) <= 0, "alignment must not be larger than natural");
					popAddress(memArg.memIdx());
					push(VecType.V128);
				}

				case MemoryInstr.V128_Load8x8_U(var memArg) -> {
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 3) <= 0, "alignment must not be larger than natural");
					popAddress(memArg.memIdx());
					push(VecType.V128);
				}

				case MemoryInstr.V128_Load16x4_S(var memArg) -> {
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 3) <= 0, "alignment must not be larger than natural");
					popAddress(memArg.memIdx());
					push(VecType.V128);
				}

				case MemoryInstr.V128_Load16x4_U(var memArg) -> {
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 3) <= 0, "alignment must not be larger than natural");
					popAddress(memArg.memIdx());
					push(VecType.V128);
				}

				case MemoryInstr.V128_Load32x2_S(var memArg) -> {
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 3) <= 0, "alignment must not be larger than natural");
					popAddress(memArg.memIdx());
					push(VecType.V128);
				}

				case MemoryInstr.V128_Load32x2_U(var memArg) -> {
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 3) <= 0, "alignment must not be larger than natural");
					popAddress(memArg.memIdx());
					push(VecType.V128);
				}

				case MemoryInstr.V128_Load8_Splat(var memArg) -> {
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 0) <= 0, "alignment must not be larger than natural");
					popAddress(memArg.memIdx());
					push(VecType.V128);
				}

				case MemoryInstr.V128_Load16_Splat(var memArg) -> {
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 1) <= 0, "alignment must not be larger than natural");
					popAddress(memArg.memIdx());
					push(VecType.V128);
				}

				case MemoryInstr.V128_Load32_Splat(var memArg) -> {
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 2) <= 0, "alignment must not be larger than natural");
					popAddress(memArg.memIdx());
					push(VecType.V128);
				}

				case MemoryInstr.V128_Load64_Splat(var memArg) -> {
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 3) <= 0, "alignment must not be larger than natural");
					popAddress(memArg.memIdx());
					push(VecType.V128);
				}

				case MemoryInstr.V128_Load32_Zero(var memArg) -> {
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 2) <= 0, "alignment must not be larger than natural");
					popAddress(memArg.memIdx());
					push(VecType.V128);
				}
				case MemoryInstr.V128_Load64_Zero(var memArg) -> {
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 3) <= 0, "alignment must not be larger than natural");
					popAddress(memArg.memIdx());
					push(VecType.V128);
				}

				case MemoryInstr.V128_Load8_Lane(var memArg, var laneIdx) -> {
					require(Byte.toUnsignedInt(laneIdx) < 16, "invalid lane index");
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 0) <= 0, "alignment must not be larger than natural");
					pop(VecType.V128);
					popAddress(memArg.memIdx());
					push(VecType.V128);
				}
				case MemoryInstr.V128_Load16_Lane(var memArg, var laneIdx) -> {
					require(Byte.toUnsignedInt(laneIdx) < 8, "invalid lane index");
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 1) <= 0, "alignment must not be larger than natural");
					pop(VecType.V128);
					popAddress(memArg.memIdx());
					push(VecType.V128);
				}
				case MemoryInstr.V128_Load32_Lane(var memArg, var laneIdx) -> {
					require(Byte.toUnsignedInt(laneIdx) < 4, "invalid lane index");
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 2) <= 0, "alignment must not be larger than natural");
					pop(VecType.V128);
					popAddress(memArg.memIdx());
					push(VecType.V128);
				}

				case MemoryInstr.V128_Load64_Lane(var memArg, var laneIdx) -> {
					require(Byte.toUnsignedInt(laneIdx) < 2, "invalid lane index");
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 3) <= 0, "alignment must not be larger than natural");
					pop(VecType.V128);
					popAddress(memArg.memIdx());
					push(VecType.V128);
				}

				case MemoryInstr.V128_Store8_Lane(var memArg, var laneIdx) -> {
					require(Byte.toUnsignedInt(laneIdx) < 16, "invalid lane index");
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 0) <= 0, "alignment must not be larger than natural");
					pop(VecType.V128);
					popAddress(memArg.memIdx());
				}

				case MemoryInstr.V128_Store16_Lane(var memArg, var laneIdx) -> {
					require(Byte.toUnsignedInt(laneIdx) < 8, "invalid lane index");
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 1) <= 0, "alignment must not be larger than natural");
					pop(VecType.V128);
					popAddress(memArg.memIdx());
				}
				case MemoryInstr.V128_Store32_Lane(var memArg, var laneIdx) -> {
					require(Byte.toUnsignedInt(laneIdx) < 4, "invalid lane index");
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 2) <= 0, "alignment must not be larger than natural");
					pop(VecType.V128);
					popAddress(memArg.memIdx());
				}
				case MemoryInstr.V128_Store64_Lane(var memArg, var laneIdx) -> {
					require(Byte.toUnsignedInt(laneIdx) < 2, "invalid lane index");
					context.requireMem(memArg.memIdx());
					require(Integer.compareUnsigned(memArg.align(), 3) <= 0, "alignment must not be larger than natural");
					pop(VecType.V128);
					popAddress(memArg.memIdx());
				}

				case MemoryInstr.Memory_Size(var memIdx) -> {
					context.requireMem(memIdx);
					pushAddress(memIdx);
				}
				case MemoryInstr.Memory_Grow(var memIdx) -> {
					context.requireMem(memIdx);
					popAddress(memIdx);
					pushAddress(memIdx);
				}

				case MemoryInstr.Memory_Fill(var memIdx) -> {
					context.requireMem(memIdx);
					popAddress(memIdx);
					pop(NumType.I32);
					popAddress(memIdx);
				}

				case MemoryInstr.Memory_Copy(var dstMemIdx, var srcMemIdx) -> {
					context.requireMem(dstMemIdx);
					context.requireMem(srcMemIdx);
					popAddress(dstMemIdx, srcMemIdx);
					popAddress(srcMemIdx);
					popAddress(dstMemIdx);
				}

				case MemoryInstr.Memory_Init(var memIdx, var dataIdx) -> {
					context.requireMem(memIdx);
					context.requireData(dataIdx);
					pop(NumType.I32);
					pop(NumType.I32);
					popAddress(memIdx);
				}

				case MemoryInstr.Data_Drop(var dataIdx) -> {
					context.requireData(dataIdx);
				}
			}
		}

		private void validateControlInstr(ControlInstr instr) throws ValidationException {
			switch(instr) {
				case ControlInstr.Nop() -> {}
				case ControlInstr.Unreachable() -> {
					stack.clear();
					unreachable = true;
				}
				case ControlInstr.Block(var blockType, var body) -> {
					tv.validateBlockType(blockType);
					var t = expandBlockType(blockType);
					var c2 = context.copy();
					c2.addLabel(t.results());
					var iv2 = new InstrValidator(c2);
					iv2.validateInstructions(body, t.args(), t.results());

					pop(t.args());
					push(t.results());
				}

				case ControlInstr.Loop(var blockType, var body) -> {
					tv.validateBlockType(blockType);
					var t = expandBlockType(blockType);
					var c2 = context.copy();
					c2.addLabel(t.args());
					var iv2 = new InstrValidator(c2);
					iv2.validateInstructions(body, t.args(), t.results());

					pop(t.args());
					push(t.results());
				}

				case ControlInstr.If(var blockType, var thenBody, var elseBody) -> {
					tv.validateBlockType(blockType);
					var t = expandBlockType(blockType);
					var c2 = context.copy();
					c2.addLabel(t.results());

					var iv2 = new InstrValidator(c2.copy());
					iv2.validateInstructions(thenBody, t.args(), t.results());

					iv2 = new InstrValidator(c2);
					iv2.validateInstructions(elseBody, t.args(), t.results());

					pop(NumType.I32);
					pop(t.args());
					push(t.results());
				}

				case ControlInstr.Throw(var tag) -> {
					context.requireTag(tag);
					var tagType = context.getTag(tag);

					context.requireFuncType(tagType.funcType());
					var funcType = (FuncType)context.getCompositeType(tagType.funcType());

					pop(funcType.args());
					stack.clear();
					unreachable = true;
				}

				case ControlInstr.Throw_Ref() -> {
					pop(new RefType(true, HeapType.AbstractHeapType.EXN));
					stack.clear();
					unreachable = true;
				}

				case ControlInstr.Br(var label) -> {
					context.requireLabel(label);
					var t = context.getLabel(label);

					pop(t);
					stack.clear();
					unreachable = true;
				}

				case ControlInstr.Br_If(var label) -> {
					context.requireLabel(label);
					var t = context.getLabel(label);

					pop(NumType.I32);
					pop(t);
					push(t);
				}

				case ControlInstr.Br_Table(var labels, var fallback) -> {
					context.requireLabel(fallback);

					pop(NumType.I32);

					ValType[] results = new ValType[context.getLabel(fallback).types().size()];

					for(int i = results.length - 1; i >= 0; --i) {
						results[i] = pop();
					}

					var resultType = new ResultType(List.of(results));

					require(subtyping.isSubtypeResult(resultType, context.getLabel(fallback)), "type mismatch");

					for(LabelIdx label : labels) {
						context.requireLabel(label);
						var t = context.getLabel(label);

						require(subtyping.isSubtypeResult(resultType, t), "type mismatch");
					}

					stack.clear();
					unreachable = true;
				}

				case ControlInstr.Br_OnNull(var label) -> {
					context.requireLabel(label);

					var labelType = context.getLabel(label);

					var t = pop();
					var refType = requireRefType(t);
					pop(labelType);
					push(labelType);
					push(new RefType(false, refType.heapType()));
				}

				case ControlInstr.Br_OnNonNull(var label) -> {
					context.requireLabel(label);

					var labelType = context.getLabel(label);
					if(labelType.types().isEmpty()) {
						throw new ValidationException("br_on_non_null target must contain at least one type");
					}

					var labelType2Types = new ArrayList<>(labelType.types());
					var lastType = labelType2Types.removeLast();
					var labelType2 = new ResultType(labelType2Types);

					var lastRefType = requireRefType(lastType);

					pop(new RefType(true, lastRefType.heapType()));
					pop(labelType2);
					push(labelType2);
				}

				case ControlInstr.Br_OnCast(var label, var t1, var t2) -> {
					context.requireLabel(label);
					
					var labelType = context.getLabel(label);
					if(labelType.types().isEmpty()) {
						throw new ValidationException("br_on_cast target must contain at least one type");
					}

					var labelType2Types = new ArrayList<>(labelType.types());
					var lastType = labelType2Types.removeLast();
					var labelType2 = new ResultType(labelType2Types);

					var lastRefType = requireRefType(lastType);
					tv.validateReferenceType(t1);
					tv.validateReferenceType(t2);

					require(subtyping.isSubtypeVal(t2, t1), "type mismatch");
					require(subtyping.isSubtypeVal(t2, lastRefType), "type mismatch");

					var t1b = new RefType(t1.isNullable() && !t2.isNullable(), t1.heapType());
					pop(t1);
					pop(labelType2);
					push(labelType2);
					push(t1b);
				}

				case ControlInstr.Br_OnCastFail(var label, var t1, var t2) -> {
					context.requireLabel(label);

					var labelType = context.getLabel(label);
					if(labelType.types().isEmpty()) {
						throw new ValidationException("br_on_cast_fail target must contain at least one type");
					}

					var labelType2Types = new ArrayList<>(labelType.types());
					var lastType = labelType2Types.removeLast();
					var labelType2 = new ResultType(labelType2Types);

					var lastRefType = requireRefType(lastType);
					tv.validateReferenceType(t1);
					tv.validateReferenceType(t2);


					var t1b = new RefType(t1.isNullable() && !t2.isNullable(), t1.heapType());

					require(subtyping.isSubtypeVal(t2, t1), "type mismatch");
					require(subtyping.isSubtypeVal(t1b, lastRefType), "type mismatch");

					pop(t1);
					pop(labelType2);
					push(labelType2);
					push(t2);
				}

				case ControlInstr.Return() -> {
					context.requireReturn();
					var t = context.getReturn();

					pop(t);
					stack.clear();
					unreachable = true;
				}

				case ControlInstr.Call(var func) -> {
					context.requireFunc(func);
					var t = context.getFuncType(func);

					pop(t.args());
					push(t.results());
				}

				case ControlInstr.Call_Ref(var funcTypeIdx) -> {
					context.requireFuncType(funcTypeIdx);
					var funcDefType = context.getType(funcTypeIdx);
					var funcType = (FuncType)context.getCompositeType(funcTypeIdx);
					pop(new RefType(true, funcDefType));
					pop(funcType.args());
					push(funcType.results());
				}

				case ControlInstr.Call_Indirect(var table, var funcType) -> {
					context.requireTable(table);

					requireFuncType(context.getTable(table).elementType().heapType());

					context.requireFuncType(funcType);
					var t = (FuncType)context.getCompositeType(funcType);

					popIndex(table);
					pop(t.args());
					push(t.results());
				}

				case ControlInstr.Return_Call(var func) -> {
					context.requireFunc(func);
					var t = context.getFuncType(func);

					pop(t.args());

					require(subtyping.isSubtypeResult(t.results(), context.getReturn()), "type mismatch");
					stack.clear();
					unreachable = true;
				}

				case ControlInstr.Return_Call_Ref(var funcTypeIdx) -> {
					context.requireFuncType(funcTypeIdx);
					var funcDefType = context.getType(funcTypeIdx);
					var funcType = (FuncType)context.getCompositeType(funcTypeIdx);
					pop(new RefType(true, funcDefType));
					pop(funcType.args());

					require(subtyping.isSubtypeResult(funcType.results(), context.getReturn()), "type mismatch");
					stack.clear();
					unreachable = true;
				}

				case ControlInstr.Return_Call_Indirect(var table, var funcType) -> {
					context.requireTable(table);

					requireFuncType(context.getTable(table).elementType().heapType());

					context.requireFuncType(funcType);
					var t = (FuncType)context.getCompositeType(funcType);

					popIndex(table);
					pop(t.args());

					require(subtyping.isSubtypeResult(t.results(), context.getReturn()), "type mismatch");
					stack.clear();
					unreachable = true;
				}

				case ControlInstr.Try_Table(var blockType, var catchClauses, var body) -> {
					tv.validateBlockType(blockType);
					var t = expandBlockType(blockType);

					for(var catchClause : catchClauses) {
						validateCatchClause(catchClause);
					}

					var c2 = context.copy();
					c2.addLabel(t.results());
					var iv2 = new InstrValidator(c2);
					iv2.validateInstructions(body, t.args(), t.results());

					pop(t.args());
					push(t.results());
				}
			}
		}

		private void validateCatchClause(ControlInstr.CatchClause catchClause) throws ValidationException {
			switch(catchClause) {
				case ControlInstr.CatchTag(var tagIdx, var labelIdx) -> {
					context.requireTag(tagIdx);
					var tag = context.getTag(tagIdx);

					context.requireFuncType(tag.funcType());
					var t = (FuncType)context.getCompositeType(tag.funcType());

					require(t.results().types().isEmpty(), "Tag type must have empty result");

					context.requireLabel(labelIdx);
					var label = context.getLabel(labelIdx);
					require(subtyping.isSubtypeResult(t.args(), label), "type mismatch: catch clause must match target block type " + t.args() + ", " + label);
				}

				case ControlInstr.CatchTagRef(var tagIdx, var labelIdx) -> {
					context.requireTag(tagIdx);
					var tag = context.getTag(tagIdx);

					context.requireFuncType(tag.funcType());
					var t = (FuncType)context.getCompositeType(tag.funcType());

					require(t.results().types().isEmpty(), "Tag type must have empty result");

					var resType = new ArrayList<ValType>();
					resType.addAll(t.args().types());
					resType.add(new RefType(false, HeapType.AbstractHeapType.EXN));

					context.requireLabel(labelIdx);
					var label = context.getLabel(labelIdx);
					require(subtyping.isSubtypeResult(new ResultType(resType), label), "type mismatch: catch_ref clause must match target block type" + resType + ", " + label);
				}

				case ControlInstr.CatchAll(var labelIdx) -> {
					context.requireLabel(labelIdx);
					var label = context.getLabel(labelIdx);

					require(label.types().isEmpty(), "type mismatch: catch_all label type must be empty");
				}

				case ControlInstr.CatchAllRef(var labelIdx) -> {
					context.requireLabel(labelIdx);
					var label = context.getLabel(labelIdx);

					var resType = new ArrayList<ValType>();
					resType.add(new RefType(false, HeapType.AbstractHeapType.EXN));

					require(subtyping.isSubtypeResult(new ResultType(resType), label), "type mismatch: catch_all_ref clause must be ref exn");
				}
			}

		}

		private FuncType expandBlockType(ControlInstr.BlockType blockType) {
			return switch(blockType) {
				case ControlInstr.BlockType.Empty() -> new FuncType(new ResultType(List.of()), new ResultType(List.of()));
				case ControlInstr.BlockType.OfIndex(var index) -> (FuncType)context.getCompositeType(index);
				case ControlInstr.BlockType.OfValType(var valType) -> new FuncType(new ResultType(List.of()), new ResultType(List.of(valType)));
			};
		}

		private void checkMemArg(NumericInstr.NumSize numSize, MemoryInstr.MemArg memArg) throws ValidationException {
			int naturalAlignment = switch(numSize) {
				case _32 -> 2;
				case _64 -> 3;
			};

			require(Integer.compareUnsigned(memArg.align(), naturalAlignment) <= 0, "alignment must not be larger than natural");


			switch(context.getMem(memArg.memIdx()).addrType()) {
				case I32 -> require(Long.compareUnsigned(memArg.offset(), 1L << 32) < 0, "offset out of range");
				case I64 -> {}
			}
		}

		private void checkVectorAlignment(MemoryInstr.MemArg memArg) throws ValidationException {
			require(Integer.compareUnsigned(memArg.align(), 4) <= 0, "alignment must not be larger than natural");

			switch(context.getMem(memArg.memIdx()).addrType()) {
				case I32 -> require(Long.compareUnsigned(memArg.offset(), 1L << 32) < 0, "offset out of range");
				case I64 -> {}
			}
		}

	}

}
