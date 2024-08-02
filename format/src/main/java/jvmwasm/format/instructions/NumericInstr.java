package jvmwasm.format.instructions;

public sealed interface NumericInstr extends Instr {
	public static enum NumSize {
		_32,
		_64,
	}

	public static record I32_Const(int value) implements NumericInstr {}
	public static record I64_Const(long value) implements NumericInstr {}
	public static record F32_Const(float value) implements NumericInstr {}
	public static record F64_Const(double value) implements NumericInstr {}


	public static enum IUnOp {
		CLZ,
		CTZ,
		POPCNT,
	}
	public static record Inn_IUnOp(NumSize size, IUnOp op) implements NumericInstr {}


	public static enum FUnOp {
		ABS,
		NEG,
		SQRT,
		CEIL,
		FLOOR,
		TRUNC,
		NEAREST,
	}
	public static record Fnn_FUnOp(NumSize size, FUnOp op) implements NumericInstr {}


	public static enum IBinOp {
		ADD,
		SUB,
		MUL,
		DIV_U,
		DIV_S,
		REM_U,
		REM_S,
		AND,
		OR,
		XOR,
		SHL,
		SHR_U,
		SHR_S,
		ROTL,
		ROTR,
	}
	public static record Inn_IBinOp(NumSize size, IBinOp op) implements NumericInstr {}


	public static enum FBinOp {
		ADD,
		SUB,
		MUL,
		DIV,
		MIN,
		MAX,
		COPYSIGN,
	}
	public static record Fnn_FBinOp(NumSize size, FBinOp op) implements NumericInstr {}


	public static enum ITestOp {
		EQZ,
	}
	public static record Inn_ITestOp(NumSize size, ITestOp op) implements NumericInstr {}

	public static enum IRelOp {
		EQ,
		NE,
		LT_U,
		LT_S,
		GT_U,
		GT_S,
		LE_U,
		LE_S,
		GE_U,
		GE_S,
	}
	public static record Inn_IRelOp(NumSize size, IRelOp op) implements NumericInstr {}

	public static enum FRelOp {
		EQ,
		NE,
		LT,
		GT,
		LE,
		GE,
	}
	public static record Fnn_FRelOp(NumSize size, FRelOp op) implements NumericInstr {}

	public static record Inn_Extend8_S(NumSize size) implements NumericInstr {}
	public static record Inn_Extend16_S(NumSize size) implements NumericInstr {}
	public static record I64_Extend32_S() implements NumericInstr {}

	public static record I32_Wrap_I64() implements NumericInstr {}
	public static record I64_Extend_I32_S() implements NumericInstr {}
	public static record I64_Extend_I32_U() implements NumericInstr {}
	public static record Inn_Trunc_Fmm_S(NumSize intSize, NumSize floatSize) implements NumericInstr {}
	public static record Inn_Trunc_Fmm_U(NumSize intSize, NumSize floatSize) implements NumericInstr {}
	public static record Inn_Trunc_Sat_Fmm_S(NumSize intSize, NumSize floatSize) implements NumericInstr {}
	public static record Inn_Trunc_Sat_Fmm_U(NumSize intSize, NumSize floatSize) implements NumericInstr {}

	public static record F32_Demote_F64() implements NumericInstr {}
	public static record F64_Promote_F32() implements NumericInstr {}
	public static record Fnn_Convert_Imm_S(NumSize floatSize, NumSize intSize) implements NumericInstr {}
	public static record Fnn_Convert_Imm_U(NumSize floatSize, NumSize intSize) implements NumericInstr {}

	public static record Inn_Reinterpret_Fnn(NumSize size) implements NumericInstr {}
	public static record Fnn_Reinterpret_Inn(NumSize size) implements NumericInstr {}


}
