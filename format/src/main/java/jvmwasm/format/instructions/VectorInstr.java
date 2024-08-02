package jvmwasm.format.instructions;

import jvmwasm.format.data.V128;

import java.math.BigInteger;
import static jvmwasm.format.instructions.NumericInstr.NumSize;

public sealed interface VectorInstr extends Instr {

	public static sealed interface AnyOp {}

	public static sealed interface I8x16_Op extends AnyOp {}
	public static sealed interface I16x8_Op extends AnyOp {}
	public static sealed interface I32x4_Op extends AnyOp {}
	public static sealed interface I64x2_Op extends AnyOp {}
	public static sealed interface VIOp extends I8x16_Op, I16x8_Op, I32x4_Op, I64x2_Op {}


	public static sealed interface F32x4_Op extends AnyOp {}
	public static sealed interface F64x2_Op extends AnyOp {}
	public static sealed interface VFOp extends F32x4_Op, F64x2_Op {}
	
	public static sealed interface VNOp extends VIOp, VFOp {}
	

	public static record V128_Const(V128 n) implements VectorInstr {}

	public static enum VVUnOp implements VectorInstr {
		NOT,
	}

	public static enum VVBinOp implements VectorInstr {
		AND,
		ANDNOT,
		OR,
		XOR,
	}

	public static enum VVTernOp implements VectorInstr {
		BITSELECT,
	}

	public static enum VVTestOp implements VectorInstr {
		ANY_TRUE,
	}
	
	public static record I8x16_Op_Instr(I8x16_Op op) implements VectorInstr {}
	public static record I16x8_Op_Instr(I16x8_Op op) implements VectorInstr {}
	public static record I32x4_Op_Instr(I32x4_Op op) implements VectorInstr {}
	public static record I64x2_Op_Instr(I64x2_Op op) implements VectorInstr {}
	public static record F32x4_Op_Instr(F32x4_Op op) implements VectorInstr {}
	public static record F64x2_Op_Instr(F64x2_Op op) implements VectorInstr {}
	
	public static record Shuffle(V128 laneIndexes) implements I8x16_Op {}

	public static record Swizzle() implements I8x16_Op {}

	public static record Splat() implements VNOp {}

	public static record ExtractLane_U(byte laneIndex) implements I8x16_Op, I16x8_Op {}
	public static record ExtractLane_S(byte laneIndex) implements I8x16_Op, I16x8_Op {}
	public static record ExtractLane(byte laneIndex) implements I32x4_Op, I64x2_Op, VFOp {}
	public static record ReplaceLane(byte laneIndex) implements VNOp {}

	public static sealed interface VNRelOp {}
	public static sealed interface VIRelOp extends I8x16_Op, I16x8_Op, I32x4_Op, VNRelOp {}
	public static enum VIRelOp_S implements VIRelOp, I64x2_Op {
		EQ,
		NE,
		LT_S,
		GT_S,
		LE_S,
		GE_S,
	}

	public static enum VIRelOp_U implements VIRelOp {
		LT_U,
		GT_U,
		LE_U,
		GE_U,
	}

	public static enum VFRelOp implements VFOp, VNRelOp {
		EQ,
		NE,
		LT,
		GT,
		LE,
		GE,
	}

	public static sealed interface VNUnOp {}

	public static enum VIUnOp implements VIOp, VNUnOp {
		ABS,
		NEG,
	}

	public static record Popcnt() implements I8x16_Op {}
	public static record Q15mulr_Sat_S() implements I16x8_Op {}
	public static record Dot_I16x8_S() implements I32x4_Op {}

	public static enum VFUnOp implements VFOp, VNUnOp {
		ABS,
		NEG,
		SQRT,
		CEIL,
		FLOOR,
		TRUNC,
		NEAREST,
	}
		
	public static record All_True() implements VIOp {}
	public static record BitMask() implements VIOp {}

	public static sealed interface VCVTop_HalfQ_Shape_ZQ {}

	public static sealed interface Narrow_Shape {}

	public static record I8x16_Narrow_I16x8_U() implements I8x16_Op, Narrow_Shape {}
	public static record I8x16_Narrow_I16x8_S() implements I8x16_Op, Narrow_Shape {}
	public static record I16x8_Narrow_I32x4_U() implements I16x8_Op, Narrow_Shape {}
	public static record I16x8_Narrow_I32x4_S() implements I16x8_Op, Narrow_Shape {}
	public static record I16x8_Extend_Low_I8x16_U() implements I16x8_Op, VCVTop_HalfQ_Shape_ZQ {}
	public static record I16x8_Extend_Low_I8x16_S() implements I16x8_Op, VCVTop_HalfQ_Shape_ZQ {}
	public static record I16x8_Extend_High_I8x16_U() implements I16x8_Op, VCVTop_HalfQ_Shape_ZQ {}
	public static record I16x8_Extend_High_I8x16_S() implements I16x8_Op, VCVTop_HalfQ_Shape_ZQ {}
	public static record I32x4_Extend_Low_I16x8_U() implements I32x4_Op, VCVTop_HalfQ_Shape_ZQ {}
	public static record I32x4_Extend_Low_I16x8_S() implements I32x4_Op, VCVTop_HalfQ_Shape_ZQ {}
	public static record I32x4_Extend_High_I16x8_U() implements I32x4_Op, VCVTop_HalfQ_Shape_ZQ {}
	public static record I32x4_Extend_High_I16x8_S() implements I32x4_Op, VCVTop_HalfQ_Shape_ZQ {}
	public static record I64x2_Extend_Low_I32x4_U() implements I64x2_Op, VCVTop_HalfQ_Shape_ZQ {}
	public static record I64x2_Extend_Low_I32x4_S() implements I64x2_Op, VCVTop_HalfQ_Shape_ZQ {}
	public static record I64x2_Extend_High_I32x4_U() implements I64x2_Op, VCVTop_HalfQ_Shape_ZQ {}
	public static record I64x2_Extend_High_I32x4_S() implements I64x2_Op, VCVTop_HalfQ_Shape_ZQ {}
	
	public static enum VIShiftOp implements VIOp {
		SHL,
		SHR_U,
		SHR_S,
	}

	public static sealed interface VNBinOp {}
	public static enum VIBinOp implements VIOp, VNBinOp {
		ADD,
		SUB,
	}
	
	public static enum VIMinMaxOp implements I8x16_Op, I16x8_Op, I32x4_Op {
		MIN_U,
		MIN_S,
		MAX_U,
		MAX_S,
	}
	
	public static enum VISatBinOp implements I8x16_Op, I16x8_Op {
		ADD_SAT_U,
		ADD_SAT_S,
		SUB_SAT_U,
		SUB_SAT_S,
	}
	
	public static enum VIMulOp implements I16x8_Op, I32x4_Op, I64x2_Op {
		MUL,
	}
	
	public static enum VIAverageOps implements I8x16_Op, I16x8_Op {
		AVGR_U,
	}

	public static sealed interface ExtMul {}
	public static sealed interface ExtAdd {}

	public static record I16x8_ExtMul_Low_I8x16_U() implements I16x8_Op, ExtMul {}
	public static record I16x8_ExtMul_Low_I8x16_S() implements I16x8_Op, ExtMul {}
	public static record I16x8_ExtMul_High_I8x16_U() implements I16x8_Op, ExtMul {}
	public static record I16x8_ExtMul_High_I8x16_S() implements I16x8_Op, ExtMul {}
	public static record I32x4_ExtMul_Low_I16x8_U() implements I32x4_Op, ExtMul {}
	public static record I32x4_ExtMul_Low_I16x8_S() implements I32x4_Op, ExtMul {}
	public static record I32x4_ExtMul_High_I16x8_U() implements I32x4_Op, ExtMul {}
	public static record I32x4_ExtMul_High_I16x8_S() implements I32x4_Op, ExtMul {}
	public static record I64x2_ExtMul_Low_I32x4_U() implements I64x2_Op, ExtMul {}
	public static record I64x2_ExtMul_Low_I32x4_S() implements I64x2_Op, ExtMul {}
	public static record I64x2_ExtMul_High_I32x4_U() implements I64x2_Op, ExtMul {}
	public static record I64x2_ExtMul_High_I32x4_S() implements I64x2_Op, ExtMul {}



	public static record I16x8_ExtAdd_Pairwise_I8x16_U() implements I16x8_Op, ExtAdd {}
	public static record I16x8_ExtAdd_Pairwise_I8x16_S() implements I16x8_Op, ExtAdd {}
	public static record I32x4_ExtAdd_Pairwise_I16x8_U() implements I32x4_Op, ExtAdd {}
	public static record I32x4_ExtAdd_Pairwise_I16x8_S() implements I32x4_Op, ExtAdd {}

	public static enum VFBinOp implements VFOp, VNBinOp {
		ADD,
		SUB,
		MUL,
		DIV,
		MIN,
		MAX,
		PMIN,
		PMAX,
	}

	public static record I32x4_Trunc_Sat_F32x4_U() implements I32x4_Op, VCVTop_HalfQ_Shape_ZQ {}
	public static record I32x4_Trunc_Sat_F32x4_S() implements I32x4_Op, VCVTop_HalfQ_Shape_ZQ {}
	public static record I32x4_Trunc_Sat_F64x4_U_Zero() implements I32x4_Op, VCVTop_HalfQ_Shape_ZQ {}
	public static record I32x4_Trunc_Sat_F64x4_S_Zero() implements I32x4_Op, VCVTop_HalfQ_Shape_ZQ {}

	public static record F32x4_Convert_I32x4_U() implements F32x4_Op, VCVTop_HalfQ_Shape_ZQ {}
	public static record F32x4_Convert_I32x4_S() implements F32x4_Op, VCVTop_HalfQ_Shape_ZQ {}
	public static record F32x4_Demote_F64x2_Zero() implements F32x4_Op, VCVTop_HalfQ_Shape_ZQ {}
	public static record F64x2_Convert_Low_I32x4_U() implements F64x2_Op, VCVTop_HalfQ_Shape_ZQ {}
	public static record F64x2_Convert_Low_I32x4_S() implements F64x2_Op, VCVTop_HalfQ_Shape_ZQ {}
	public static record F64x2_Promote_Low_F32x4() implements F64x2_Op, VCVTop_HalfQ_Shape_ZQ {}
	
}

