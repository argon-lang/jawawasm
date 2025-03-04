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
	}

	public void validateExpr(Expr expr, ResultType resultType) throws ValidationException {
		validateInstructions(expr.body(), new ResultType(List.of()), resultType);
	}

	public void validateInstructions(List<? extends Instr> instrs, ResultType argType, ResultType resultType) throws ValidationException {
		System.err.println(instrs);
		List<OperandType> stack = new ArrayList<>(argType.types().size());
		for(ValType t : argType.types()) {
			stack.add(new OperandType.OfValType(t));
		}

		var sv = new StackValidator(stack);
		for(Instr instr : instrs) {
			sv.validateInstr(instr);
		}

		System.err.println(resultType);
		System.err.println(stack);

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
			case ReferenceInstr.Ref_Func(_) -> {}
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
			context.requireType(funcType);
		}
		else if(!(heapType instanceof BotType || heapType instanceof FuncType || heapType == HeapType.AbstractHeapType.FUNC)) {
			throw new ValidationException("type mismatch");
		}
	}

	private final class StackValidator {
		public StackValidator(List<OperandType> stack) {
			this.stack = stack;

		}

		private final List<OperandType> stack;
		private boolean unreachable = false;

		private void push(OperandType t) {
			stack.add(t);
		}

		private void push(ValType t) {
			stack.add(new OperandType.OfValType(t));
		}

		private void push(ResultType t) {
			for(var t2 : t.types()) {
				push(t2);
			}
		}

		private void pushAddress(MemIdx memIdx) throws ValidationException {
			push(context.getMem(memIdx).addrType().asNumType());
		}

		private void pushIndex(TableIdx tableIdx) throws ValidationException {
			push(context.getTable(tableIdx).addrType().asNumType());
		}

		private OperandType pop() throws ValidationException {
			if(stack.isEmpty()) {
				if(unreachable) {
					return new OperandType.Bottom();
				}
				else {
					throw new ValidationException("type mismatch", "type mismatch due to empty stack");
				}
			}

			return stack.removeLast();
		}

		private void pop(OperandType t) throws ValidationException {
			switch(pop()) {
				case OperandType.OfValType(var t2) -> {
					switch(t) {
						case OperandType.Bottom() -> throw new ValidationException("type mismatch");
						case OperandType.OfValType(var t3) -> require(t2.equals(t3), "type mismatch");
					}
				}

				case OperandType.Bottom() -> {}
			}
		}

		private void pop(ValType t) throws ValidationException {
			switch(pop()) {
				case OperandType.OfValType(var t2) -> {
					if(t2 != null && !new Subtyping(context).isSubtypeVal(t2, t)) {
						throw new ValidationException("type mismatch", "type mismatch expected: " + t + ", actual: " + t2);
					}
				}

				case OperandType.Bottom() -> {}
			}
		}

		private void pop(ResultType t) throws ValidationException {
			for(int i = t.types().size() - 1; i >= 0; --i) {
				pop(t.types().get(i));
			}
		}

		private void popAddress(MemIdx memIdx) throws ValidationException {
			pop(context.getMem(memIdx).addrType().asNumType());
		}

		private void popAddress(MemIdx memIdx1, MemIdx memIdx2) throws ValidationException {
			switch(context.getMem(memIdx1).addrType()) {
				case I32 -> pop(NumType.I32);
				case I64 -> popAddress(memIdx2);
			}
		}

		private void popIndex(TableIdx tableIdx) throws ValidationException {
			pop(context.getTable(tableIdx).addrType().asNumType());
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
				case ReferenceInstr.Ref_Null(var t) -> push(new RefType(true, t));
				case ReferenceInstr.Ref_IsNull() -> {
					switch(pop()) {
						case OperandType.Bottom _ -> {}
						case OperandType.OfValType(var t) -> requireRefType(t);
					}
					push(NumType.I32);
				}
				case ReferenceInstr.Ref_Func(var funcIdx) -> {
					context.requireRef(funcIdx);
					context.requireFunc(funcIdx);
					push(new OperandType.OfValType(new RefType(false, context.getFunc(funcIdx))));
				}
				case ReferenceInstr.Ref_AsNonNull() -> {
					switch(pop()) {
						case OperandType.Bottom _ -> {}
						case OperandType.OfValType(var t) -> {
							var refType = requireRefType(t);
							push(new RefType(false, refType.heapType()));
						}
					}
				}
			}
		}

		private void validateVectorInstr(VectorInstr instr) throws ValidationException {
			switch(instr) {
				case VectorInstr.V128_Const(var v) -> push(VecType.V128);

				case VectorInstr.VVUnOp vvUnOp -> {
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.VVBinOp vvBinOp -> {
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.VVTernOp vvTernOp -> {
					pop(VecType.V128);
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.VVTestOp vvTestOp -> {
					pop(VecType.V128);
					push(NumType.I32);
				}

				case VectorInstr.I8x16_Op_Instr(var op) -> validateVectorInstrOp(op, NumType.I32, 16);
				case VectorInstr.I16x8_Op_Instr(var op) -> validateVectorInstrOp(op, NumType.I32, 8);
				case VectorInstr.I32x4_Op_Instr(var op) -> validateVectorInstrOp(op, NumType.I32, 4);
				case VectorInstr.I64x2_Op_Instr(var op) -> validateVectorInstrOp(op, NumType.I64, 2);
				case VectorInstr.F32x4_Op_Instr(var op) -> validateVectorInstrOp(op, NumType.F32, 4);
				case VectorInstr.F64x2_Op_Instr(var op) -> validateVectorInstrOp(op, NumType.F64, 2);
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

				case VectorInstr.VNUnOp vnUnOp -> {
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.VNBinOp vnBinOp -> {
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.VNRelOp vnRelOp -> {
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.VIShiftOp viShiftOp -> {
					pop(NumType.I32);
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.All_True() -> {
					pop(VecType.V128);
					push(NumType.I32);
				}

				case VectorInstr.VCVTop_HalfQ_Shape_ZQ vcvTopHalfQShapeZq -> {
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.Narrow_Shape vcvTopHalfQShapeZq -> {
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

				case VectorInstr.ExtMul extMul -> {
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.ExtAdd extAdd -> {
					pop(VecType.V128);
					push(VecType.V128);
				}

				case VectorInstr.Q15mulr_Sat_S q15mulrSatS -> {
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}
				case VectorInstr.VIAverageOps viAverageOps -> {
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}
				case VectorInstr.VIMinMaxOp viMinMaxOp -> {
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}
				case VectorInstr.VIMulOp viMulOp -> {
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}
				case VectorInstr.VISatBinOp viSatBinOp -> {
					pop(VecType.V128);
					pop(VecType.V128);
					push(VecType.V128);
				}
				case VectorInstr.Popcnt popcnt -> {
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
							case OperandType.Bottom bottom -> t = pop();
							case OperandType.OfValType(var t2) -> {
								require(t2 instanceof NumType || t2 instanceof VecType, "type mismatch");
								pop(t2);
							}
						}
						push(t);
					}
					else {
						require(types.size() == 1, "invalid result arity");
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
					push(context.getLocal(local));
				}
				case VariableInstr.Local_Set(var local) -> {
					context.requireLocal(local);
					pop(context.getLocal(local));
				}
				case VariableInstr.Local_Tee(var local) -> {
					context.requireLocal(local);
					pop(context.getLocal(local));
					push(context.getLocal(local));
				}

				case VariableInstr.Global_Get(var global) -> {
					context.requireGlobal(global);
					push(context.getGlobal(global).type());
				}
				case VariableInstr.Global_Set(var global) -> {
					context.requireGlobal(global);
					require(context.getGlobal(global).mutability() == Mut.Var, "global is immutable");
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
					require(t1.elementType().equals(t2.elementType()), "type mismatch");

					popIndex(dest, src);
					popIndex(src);
					popIndex(dest);
				}

				case TableInstr.Table_Init(var tableIdx, var elemIdx) -> {
					context.requireTable(tableIdx);
					var t1 = context.getTable(tableIdx);
					context.requireElem(elemIdx);
					var t2 = context.getElem(elemIdx);
					require(t1.elementType().equals(t2), "type mismatch");

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
					checkNumSizeAlignment(numSize, memArg);
					popAddress(memArg.memIdx());
					push(intTypeForSize(numSize));
				}

				case MemoryInstr.Fnn_Load(var numSize, var memArg) -> {
					context.requireMem(memArg.memIdx());
					checkNumSizeAlignment(numSize, memArg);
					popAddress(memArg.memIdx());
					push(floatTypeForSize(numSize));
				}

				case MemoryInstr.Inn_Store(var numSize, var memArg) -> {
					context.requireMem(memArg.memIdx());
					checkNumSizeAlignment(numSize, memArg);
					pop(intTypeForSize(numSize));
					popAddress(memArg.memIdx());
				}

				case MemoryInstr.Fnn_Store(var numSize, var memArg) -> {
					context.requireMem(memArg.memIdx());
					checkNumSizeAlignment(numSize, memArg);
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
					new TypeValidator(context).validateBlockType(blockType);
					var t = expandBlockType(blockType);
					var c2 = context.copy();
					c2.addLabel(t.results());
					var iv2 = new InstrValidator(c2);
					iv2.validateInstructions(body, t.args(), t.results());

					pop(t.args());
					push(t.results());
				}

				case ControlInstr.Loop(var blockType, var body) -> {
					new TypeValidator(context).validateBlockType(blockType);
					var t = expandBlockType(blockType);
					var c2 = context.copy();
					c2.addLabel(t.args());
					var iv2 = new InstrValidator(c2);
					iv2.validateInstructions(body, t.args(), t.results());

					pop(t.args());
					push(t.results());
				}

				case ControlInstr.If(var blockType, var thenBody, var elseBody) -> {
					new TypeValidator(context).validateBlockType(blockType);
					var t = expandBlockType(blockType);
					var c2 = context.copy();
					c2.addLabel(t.results());

					var iv2 = new InstrValidator(c2);
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

					context.requireType(tagType.funcType());
					var funcType = context.getType(tagType.funcType());

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

					List<OperandType> unifiedLabelType = new ArrayList<>();
					for(var t : context.getLabel(fallback).types()) {
						unifiedLabelType.add(new OperandType.OfValType(t));
					}

					for(LabelIdx label : labels) {
						context.requireLabel(label);
						var t = context.getLabel(label);

						require(unifiedLabelType.size() == t.types().size(), "type mismatch");

						for(int i = 0; i < t.types().size(); ++i) {
							int j = unifiedLabelType.size() - t.types().size() + i;
							switch(unifiedLabelType.get(j)) {
								case OperandType.Bottom bottom -> {}
								case OperandType.OfValType(var utj) -> {
									if(!t.types().get(i).equals(utj)) {
										unifiedLabelType.set(j, new OperandType.Bottom());
									}
								}
							}
						}
					}

					pop(NumType.I32);
					for(int i = unifiedLabelType.size() - 1; i >= 0; --i) {
						pop(unifiedLabelType.get(i));
					}
					stack.clear();
					unreachable = true;
				}

				case ControlInstr.Br_OnNull(var label) -> {
					context.requireLabel(label);

					var labelType = context.getLabel(label);

					switch(pop()) {
						case OperandType.Bottom _ -> {}
						case OperandType.OfValType(var t) -> {
							var refType = requireRefType(t);
							pop(labelType);
							push(labelType);
							push(new RefType(false, refType.heapType()));
						}
					}
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
					push(labelType);
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
					context.requireType(funcTypeIdx);
					var funcType = context.getType(funcTypeIdx);
					pop(new RefType(true, funcType));
					pop(funcType.args());
					push(funcType.results());
				}

				case ControlInstr.Call_Indirect(var table, var funcType) -> {
					context.requireTable(table);

					requireFuncType(context.getTable(table).elementType().heapType());

					context.requireType(funcType);
					var t = context.getType(funcType);

					System.err.println(stack);

					popIndex(table);
					pop(t.args());
					push(t.results());

					System.err.println(stack);
				}

				case ControlInstr.Return_Call(var func) -> {
					context.requireFunc(func);
					var t = context.getFuncType(func);

					pop(t.args());

					require(new Subtyping(context).isSubtypeResult(t.results(), context.getReturn()), "type mismatch");
					stack.clear();
					unreachable = true;
				}

				case ControlInstr.Return_Call_Ref(var funcTypeIdx) -> {
					context.requireType(funcTypeIdx);
					var funcType = context.getType(funcTypeIdx);
					pop(new RefType(true, funcType));
					pop(funcType.args());

					require(new Subtyping(context).isSubtypeResult(funcType.results(), context.getReturn()), "type mismatch");
					stack.clear();
					unreachable = true;
				}

				case ControlInstr.Return_Call_Indirect(var table, var funcType) -> {
					context.requireTable(table);

					requireFuncType(context.getTable(table).elementType().heapType());

					context.requireType(funcType);
					var t = context.getType(funcType);

					popIndex(table);
					pop(t.args());

					require(new Subtyping(context).isSubtypeResult(t.results(), context.getReturn()), "type mismatch");
					stack.clear();
					unreachable = true;
				}

				case ControlInstr.Try_Table(var blockType, var catchClauses, var body) -> {
					new TypeValidator(context).validateBlockType(blockType);
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

					context.requireType(tag.funcType());
					var t = context.getType(tag.funcType());

					require(t.results().types().isEmpty(), "Tag type must have empty result");

					context.requireLabel(labelIdx);
					var label = context.getLabel(labelIdx);
					require(new Subtyping(context).isSubtypeResult(t.args(), label), "type mismatch", "catch clause must match target block type " + t.args() + ", " + label);
				}

				case ControlInstr.CatchTagRef(var tagIdx, var labelIdx) -> {
					context.requireTag(tagIdx);
					var tag = context.getTag(tagIdx);

					context.requireType(tag.funcType());
					var t = context.getType(tag.funcType());

					require(t.results().types().isEmpty(), "Tag type must have empty result");

					var resType = new ArrayList<ValType>();
					resType.addAll(t.args().types());
					resType.add(new RefType(false, HeapType.AbstractHeapType.EXN));

					context.requireLabel(labelIdx);
					var label = context.getLabel(labelIdx);
					require(new Subtyping(context).isSubtypeResult(new ResultType(resType), label), "type mismatch", "catch_ref clause must match target block type" + resType + ", " + label);
				}

				case ControlInstr.CatchAll(var labelIdx) -> {
					context.requireLabel(labelIdx);
					var label = context.getLabel(labelIdx);

					require(label.types().isEmpty(), "type mismatch", "catch_all label type must be empty");
				}

				case ControlInstr.CatchAllRef(var labelIdx) -> {
					context.requireLabel(labelIdx);
					var label = context.getLabel(labelIdx);

					var resType = new ArrayList<ValType>();
					resType.add(new RefType(false, HeapType.AbstractHeapType.EXN));

					require(new Subtyping(context).isSubtypeResult(new ResultType(resType), label), "type mismatch", "catch_all_ref clause must be ref exn");
				}
			}

		}

		private FuncType expandBlockType(ControlInstr.BlockType blockType) {
			return switch(blockType) {
				case ControlInstr.BlockType.Empty() -> new FuncType(new ResultType(List.of()), new ResultType(List.of()));
				case ControlInstr.BlockType.OfIndex(var index) -> context.getType(index);
				case ControlInstr.BlockType.OfValType(var valType) -> new FuncType(new ResultType(List.of()), new ResultType(List.of(valType)));
			};
		}

		private void checkNumSizeAlignment(NumericInstr.NumSize numSize, MemoryInstr.MemArg memArg) throws ValidationException {
			switch(numSize) {
				case _32 -> require(Integer.compareUnsigned(memArg.align(), 2) <= 0, "alignment must not be larger than natural");
				case _64 -> require(Integer.compareUnsigned(memArg.align(), 3) <= 0, "alignment must not be larger than natural");
			}
		}

		private void checkVectorAlignment(MemoryInstr.MemArg memArg) throws ValidationException {
			require(Integer.compareUnsigned(memArg.align(), 4) <= 0, "alignment must not be larger than natural");
		}

	}

}
