package dev.argon.jvmwasm.format.instructions;

import dev.argon.jvmwasm.format.data.V128;

/**
 * Vector instructions
 */
public sealed interface VectorInstr extends Instr {

	/**
	 * Base type for vector operations
	 */
	public static sealed interface AnyOp {}


	/**
	 * Base type for i8x16 operations.
	 */
	public static sealed interface I8x16_Op extends AnyOp {}
	/**
	 * Base type for i16x8 operations.
	 */
	public static sealed interface I16x8_Op extends AnyOp {}
	/**
	 * Base type for i32x4 operations.
	 */
	public static sealed interface I32x4_Op extends AnyOp {}
	/**
	 * Base type for i64x2 operations.
	 */
	public static sealed interface I64x2_Op extends AnyOp {}
	/**
	 * Base type for vector integer operations.
	 */
	public static sealed interface VIOp extends I8x16_Op, I16x8_Op, I32x4_Op, I64x2_Op {}


	/**
	 * Base type for f32x4 operations.
	 */
	public static sealed interface F32x4_Op extends AnyOp {}
	/**
	 * Base type for f64x2 operations.
	 */
	public static sealed interface F64x2_Op extends AnyOp {}
	/**
	 * Base type for vector float operations.
	 */
	public static sealed interface VFOp extends F32x4_Op, F64x2_Op {}
	/**
	 * Base type for vector number operations.
	 */

	public static sealed interface VNOp extends VIOp, VFOp {}

	/**
	 * WebAssembly `v128.const` instruction
	 * @param n The constant value.
	 */
	public static record V128_Const(V128 n) implements VectorInstr {}

	/**
	 * Vector unary operation
	 */
	public static enum VVUnOp implements VectorInstr {
		/**
		 * ~
		 */
		NOT,
	}

	/**
	 * Vector binary operations
	 */
	public static enum VVBinOp implements VectorInstr {
		/**
		 * &amp;
		 */
		AND,
		/**
		 * &amp;~
		 */
		ANDNOT,
		/**
		 * |
		 */
		OR,
		/**
		 * ^
		 */
		XOR,
	}

	/**
	 * Vector ternary operation
	 */
	public static enum VVTernOp implements VectorInstr {
		/**
		 * bitselect
		 */
		BITSELECT,
	}

	/**
	 * Vector test operation
	 */
	public static enum VVTestOp implements VectorInstr {
		/**
		 * any_true
		 */
		ANY_TRUE,
	}
	/**
	 * WebAssembly `v128.i8x16_op` instruction
	 * @param op The operation.
	 */
	public static record I8x16_Op_Instr(I8x16_Op op) implements VectorInstr {}

	/**
	 * WebAssembly `v128.i16x8_op` instruction
	 * @param op The operation.
	 */
	public static record I16x8_Op_Instr(I16x8_Op op) implements VectorInstr {}

	/**
	 * WebAssembly `v128.i32x4_op` instruction
	 * @param op The operation.
	 */
	public static record I32x4_Op_Instr(I32x4_Op op) implements VectorInstr {}

	/**
	 * WebAssembly `v128.i64x2_op` instruction
	 * @param op The operation.
	 */
	public static record I64x2_Op_Instr(I64x2_Op op) implements VectorInstr {}

	/**
	 * WebAssembly `v128.f32x4_op` instruction
	 * @param op The operation.
	 */
	public static record F32x4_Op_Instr(F32x4_Op op) implements VectorInstr {}

	/**
	 * WebAssembly `v128.f64x2_op` instruction
	 * @param op The operation.
	 */
	public static record F64x2_Op_Instr(F64x2_Op op) implements VectorInstr {}


	/**
	 * WebAssembly `v128.i8x16_shuffle` instruction
	 * @param laneIndexes The lane indexes.
	 */
	public static record Shuffle(V128 laneIndexes) implements I8x16_Op {}

	/**
	 * WebAssembly `v128.i8x16_swizzle` instruction
	 */
	public static record Swizzle() implements I8x16_Op {}

	/**
	 * WebAssembly `splat` instruction
	 */
	public static record Splat() implements VNOp {}

	/**
	 * WebAssembly `extract_lane_u` instruction
	 * @param laneIndex The lane index.
	 */
	public static record ExtractLane_U(byte laneIndex) implements I8x16_Op, I16x8_Op {}
	/**
	 * WebAssembly `extract_lane_s` instruction
	 * @param laneIndex The lane index.
	 */
	public static record ExtractLane_S(byte laneIndex) implements I8x16_Op, I16x8_Op {}
	/**
	 * WebAssembly `extract_lane` instruction
	 * @param laneIndex The lane index.
	 */
	public static record ExtractLane(byte laneIndex) implements I32x4_Op, I64x2_Op, VFOp {}
	/**
	 * WebAssembly `replace_lane` instruction
	 * @param laneIndex The lane index.
	 */
	public static record ReplaceLane(byte laneIndex) implements VNOp {}
	/**
	 * Vector number relational operations.
	 */
	public static sealed interface VNRelOp {}

	/**
	 * Vector integer relational operations.
	 */
	public static sealed interface VIRelOp extends I8x16_Op, I16x8_Op, I32x4_Op, VNRelOp {}
	/**
	 * Signed comparison operations
	 */
	public static enum VIRelOp_S implements VIRelOp, I64x2_Op {
		/**
		 * ==
		 */
		EQ,
		/**
		 * !=
		 */
		NE,
		/**
		 * &lt;
		 */
		LT_S,
		/**
		 * >
		 */
		GT_S,
		/**
		 * &lt;=
		 */
		LE_S,
		/**
		 * >=
		 */
		GE_S,
	}

	/**
	 * Unsigned comparison operations
	 */
	public static enum VIRelOp_U implements VIRelOp {
		/**
		 * &lt;
		 */
		LT_U,
		/**
		 * >
		 */
		GT_U,
		/**
		 * &lt;=
		 */
		LE_U,
		/**
		 * >=
		 */
		GE_U,
	}

	/**
	 * Floating-point comparison operations
	 */
	public static enum VFRelOp implements VFOp, VNRelOp {
		/**
		 * ==
		 */
		EQ,
		/**
		 * !=
		 */
		NE,
		/**
		 * &lt;
		 */
		LT,
		/**
		 * >
		 */
		GT,
		/**
		 * &lt;=
		 */
		LE,
		/**
		 * >=
		 */
		GE,
	}


	/**
	 * Vector unary number operations.
	 */
	public static sealed interface VNUnOp {}

	/**
	 * Integer unary operations
	 */
	public static enum VIUnOp implements VIOp, VNUnOp {
		/**
		 * abs
		 */
		ABS,
		/**
		 * -
		 */
		NEG,
	}

	/**
	 * WebAssembly `v128.i8x16_popcnt` instruction
	 */
	public static record Popcnt() implements I8x16_Op {}
	/**
	 * WebAssembly `v128.i16x8_q15mulr_sat_s` instruction
	 */
	public static record Q15mulr_Sat_S() implements I16x8_Op {}
	/**
	 * WebAssembly `v128.i32x4_dot_i16x8_s` instruction
	 */
	public static record Dot_I16x8_S() implements I32x4_Op {}

	/**
	 * Floating-point unary operations
	 */
	public static enum VFUnOp implements VFOp, VNUnOp {
		/**
		 * abs
		 */
		ABS,
		/**
		 * -
		 */
		NEG,
		/**
		 * sqrt
		 */
		SQRT,
		/**
		 * ceil
		 */
		CEIL,
		/**
		 * floor
		 */
		FLOOR,
		/**
		 * trunc
		 */
		TRUNC,
		/**
		 * nearest
		 */
		NEAREST,
	}

	/**
	 * WebAssembly `all_true` instruction
	 */
	public static record All_True() implements VIOp {}
	/**
	 * WebAssembly `bitmask` instruction
	 */
	public static record BitMask() implements VIOp {}

	/**
	 * VCVTop_HalfQ_Shape_ZQ operations
	 */
	public static sealed interface VCVTop_HalfQ_Shape_ZQ {}

	/**
	 * Narrow_Shape operations
	 */
	public static sealed interface Narrow_Shape {}

	/**
	 * WebAssembly `v128.i8x16_narrow_i16x8_u` instruction
	 */
	public static record I8x16_Narrow_I16x8_U() implements I8x16_Op, Narrow_Shape {}
	/**
	 * WebAssembly `v128.i8x16_narrow_i16x8_s` instruction
	 */
	public static record I8x16_Narrow_I16x8_S() implements I8x16_Op, Narrow_Shape {}
	/**
	 * WebAssembly `v128.i16x8_narrow_i32x4_u` instruction
	 */
	public static record I16x8_Narrow_I32x4_U() implements I16x8_Op, Narrow_Shape {}
	/**
	 * WebAssembly `v128.i16x8_narrow_i32x4_s` instruction
	 */
	public static record I16x8_Narrow_I32x4_S() implements I16x8_Op, Narrow_Shape {}
	/**
	 * WebAssembly `v128.i16x8_extend_low_i8x16_u` instruction
	 */
	public static record I16x8_Extend_Low_I8x16_U() implements I16x8_Op, VCVTop_HalfQ_Shape_ZQ {}
	/**
	 * WebAssembly `v128.i16x8_extend_low_i8x16_s` instruction
	 */
	public static record I16x8_Extend_Low_I8x16_S() implements I16x8_Op, VCVTop_HalfQ_Shape_ZQ {}
	/**
	 * WebAssembly `v128.i16x8_extend_high_i8x16_u` instruction
	 */
	public static record I16x8_Extend_High_I8x16_U() implements I16x8_Op, VCVTop_HalfQ_Shape_ZQ {}
	/**
	 * WebAssembly `v128.i16x8_extend_high_i8x16_s` instruction
	 */
	public static record I16x8_Extend_High_I8x16_S() implements I16x8_Op, VCVTop_HalfQ_Shape_ZQ {}
	/**
	 * WebAssembly `v128.i32x4_extend_low_i16x8_u` instruction
	 */
	public static record I32x4_Extend_Low_I16x8_U() implements I32x4_Op, VCVTop_HalfQ_Shape_ZQ {}
	/**
	 * WebAssembly `v128.i32x4_extend_low_i16x8_s` instruction
	 */
	public static record I32x4_Extend_Low_I16x8_S() implements I32x4_Op, VCVTop_HalfQ_Shape_ZQ {}
	/**
	 * WebAssembly `v128.i32x4_extend_high_i16x8_u` instruction
	 */
	public static record I32x4_Extend_High_I16x8_U() implements I32x4_Op, VCVTop_HalfQ_Shape_ZQ {}
	/**
	 * WebAssembly `v128.i32x4_extend_high_i16x8_s` instruction
	 */
	public static record I32x4_Extend_High_I16x8_S() implements I32x4_Op, VCVTop_HalfQ_Shape_ZQ {}
	/**
	 * WebAssembly `v128.i64x2_extend_low_i32x4_u` instruction
	 */
	public static record I64x2_Extend_Low_I32x4_U() implements I64x2_Op, VCVTop_HalfQ_Shape_ZQ {}
	/**
	 * WebAssembly `v128.i64x2_extend_low_i32x4_s` instruction
	 */
	public static record I64x2_Extend_Low_I32x4_S() implements I64x2_Op, VCVTop_HalfQ_Shape_ZQ {}
	/**
	 * WebAssembly `v128.i64x2_extend_high_i32x4_u` instruction
	 */
	public static record I64x2_Extend_High_I32x4_U() implements I64x2_Op, VCVTop_HalfQ_Shape_ZQ {}
	/**
	 * WebAssembly `v128.i64x2_extend_high_i32x4_s` instruction
	 */
	public static record I64x2_Extend_High_I32x4_S() implements I64x2_Op, VCVTop_HalfQ_Shape_ZQ {}

	/**
	 * Shift operations
	 */
	public static enum VIShiftOp implements VIOp {
		/**
		 * &lt;&lt;
		 */
		SHL,
		/**
		 * >>>
		 */
		SHR_U,
		/**
		 * >>
		 */
		SHR_S,
	}

	/**
	 * Vector binary number operations.
	 */
	public static sealed interface VNBinOp {}

	/**
	 * Basic binary operations
	 */
	public static enum VIBinOp implements VIOp, VNBinOp {
		/**
		 * +
		 */
		ADD,
		/**
		 * -
		 */
		SUB,
	}

	/**
	 * Minimum and maximum operations
	 */
	public static enum VIMinMaxOp implements I8x16_Op, I16x8_Op, I32x4_Op {
		/**
		 * min_u
		 */
		MIN_U,
		/**
		 * min_s
		 */
		MIN_S,
		/**
		 * max_u
		 */
		MAX_U,
		/**
		 * max_s
		 */
		MAX_S,
	}

	/**
	 * Saturating binary operations
	 */
	public static enum VISatBinOp implements I8x16_Op, I16x8_Op {
		/**
		 * + (saturating, unsigned)
		 */
		ADD_SAT_U,
		/**
		 * + (saturating, signed)
		 */
		ADD_SAT_S,
		/**
		 * - (saturating, unsigned)
		 */
		SUB_SAT_U,
		/**
		 * - (saturating, signed)
		 */
		SUB_SAT_S,
	}

	/**
	 * Multiplication operations
	 */
	public static enum VIMulOp implements I16x8_Op, I32x4_Op, I64x2_Op {
		/**
		 * *
		 */
		MUL,
	}

	/**
	 * Average operations
	 */
	public static enum VIAverageOps implements I8x16_Op, I16x8_Op {
		/**
		 * Average (unsigned)
		 */
		AVGR_U,
	}

	/**
	 * ExtMul instructions
	 */
	public static sealed interface ExtMul {}

	/**
	 * ExtAdd instructions
	 */
	public static sealed interface ExtAdd {}

	/**
	 * WebAssembly `v128.i16x8_extmul_low_i8x16_u` instruction
	 */
	public static record I16x8_ExtMul_Low_I8x16_U() implements I16x8_Op, ExtMul {}
	/**
	 * WebAssembly `v128.i16x8_extmul_low_i8x16_s` instruction
	 */
	public static record I16x8_ExtMul_Low_I8x16_S() implements I16x8_Op, ExtMul {}
	/**
	 * WebAssembly `v128.i16x8_extmul_high_i8x16_u` instruction
	 */
	public static record I16x8_ExtMul_High_I8x16_U() implements I16x8_Op, ExtMul {}
	/**
	 * WebAssembly `v128.i16x8_extmul_high_i8x16_s` instruction
	 */
	public static record I16x8_ExtMul_High_I8x16_S() implements I16x8_Op, ExtMul {}
	/**
	 * WebAssembly `v128.i32x4_extmul_low_i16x8_u` instruction
	 */
	public static record I32x4_ExtMul_Low_I16x8_U() implements I32x4_Op, ExtMul {}
	/**
	 * WebAssembly `v128.i32x4_extmul_low_i16x8_s` instruction
	 */
	public static record I32x4_ExtMul_Low_I16x8_S() implements I32x4_Op, ExtMul {}
	/**
	 * WebAssembly `v128.i32x4_extmul_high_i16x8_u` instruction
	 */
	public static record I32x4_ExtMul_High_I16x8_U() implements I32x4_Op, ExtMul {}
	/**
	 * WebAssembly `v128.i32x4_extmul_high_i16x8_s` instruction
	 */
	public static record I32x4_ExtMul_High_I16x8_S() implements I32x4_Op, ExtMul {}
	/**
	 * WebAssembly `v128.i64x2_extmul_low_i32x4_u` instruction
	 */
	public static record I64x2_ExtMul_Low_I32x4_U() implements I64x2_Op, ExtMul {}
	/**
	 * WebAssembly `v128.i64x2_extmul_low_i32x4_s` instruction
	 */
	public static record I64x2_ExtMul_Low_I32x4_S() implements I64x2_Op, ExtMul {}
	/**
	 * WebAssembly `v128.i64x2_extmul_high_i32x4_u` instruction
	 */
	public static record I64x2_ExtMul_High_I32x4_U() implements I64x2_Op, ExtMul {}
	/**
	 * WebAssembly `v128.i64x2_extmul_high_i32x4_s` instruction
	 */
	public static record I64x2_ExtMul_High_I32x4_S() implements I64x2_Op, ExtMul {}

	/**
	 * WebAssembly `v128.i16x8_extadd_pairwise_i8x16_u` instruction
	 */
	public static record I16x8_ExtAdd_Pairwise_I8x16_U() implements I16x8_Op, ExtAdd {}
	/**
	 * WebAssembly `v128.i16x8_extadd_pairwise_i8x16_s` instruction
	 */
	public static record I16x8_ExtAdd_Pairwise_I8x16_S() implements I16x8_Op, ExtAdd {}
	/**
	 * WebAssembly `v128.i32x4_extadd_pairwise_i16x8_u` instruction
	 */
	public static record I32x4_ExtAdd_Pairwise_I16x8_U() implements I32x4_Op, ExtAdd {}
	/**
	 * WebAssembly `v128.i32x4_extadd_pairwise_i16x8_s` instruction
	 */
	public static record I32x4_ExtAdd_Pairwise_I16x8_S() implements I32x4_Op, ExtAdd {}
	
	/**
	 * Floating-point binary operations
	 */
	public static enum VFBinOp implements VFOp, VNBinOp {
		/**
		 * +
		 */
		ADD,
		/**
		 * -
		 */
		SUB,
		/**
		 * *
		 */
		MUL,
		/**
		 * /
		 */
		DIV,
		/**
		 * min
		 */
		MIN,
		/**
		 * max
		 */
		MAX,
		/**
		 * pmin
		 */
		PMIN,
		/**
		 * pmax
		 */
		PMAX,
	}


	/**
	 * WebAssembly `v128.i32x4_trunc_sat_f32x4_u` instruction
	 */
	public static record I32x4_Trunc_Sat_F32x4_U() implements I32x4_Op, VCVTop_HalfQ_Shape_ZQ {}
	/**
	 * WebAssembly `v128.i32x4_trunc_sat_f32x4_s` instruction
	 */
	public static record I32x4_Trunc_Sat_F32x4_S() implements I32x4_Op, VCVTop_HalfQ_Shape_ZQ {}
	/**
	 * WebAssembly `v128.i32x4_trunc_sat_f64x4_u_zero` instruction
	 */
	public static record I32x4_Trunc_Sat_F64x4_U_Zero() implements I32x4_Op, VCVTop_HalfQ_Shape_ZQ {}
	/**
	 * WebAssembly `v128.i32x4_trunc_sat_f64x4_s_zero` instruction
	 */
	public static record I32x4_Trunc_Sat_F64x4_S_Zero() implements I32x4_Op, VCVTop_HalfQ_Shape_ZQ {}

	/**
	 * WebAssembly `v128.f32x4_convert_i32x4_u` instruction
	 */
	public static record F32x4_Convert_I32x4_U() implements F32x4_Op, VCVTop_HalfQ_Shape_ZQ {}
	/**
	 * WebAssembly `v128.f32x4_convert_i32x4_s` instruction
	 */
	public static record F32x4_Convert_I32x4_S() implements F32x4_Op, VCVTop_HalfQ_Shape_ZQ {}
	/**
	 * WebAssembly `v128.f32x4_demote_f64x2_zero` instruction
	 */
	public static record F32x4_Demote_F64x2_Zero() implements F32x4_Op, VCVTop_HalfQ_Shape_ZQ {}
	/**
	 * WebAssembly `v128.f64x2_convert_low_i32x4_u` instruction
	 */
	public static record F64x2_Convert_Low_I32x4_U() implements F64x2_Op, VCVTop_HalfQ_Shape_ZQ {}
	/**
	 * WebAssembly `v128.f64x2_convert_low_i32x4_s` instruction
	 */
	public static record F64x2_Convert_Low_I32x4_S() implements F64x2_Op, VCVTop_HalfQ_Shape_ZQ {}
	/**
	 * WebAssembly `v128.f64x2_promote_low_f32x4` instruction
	 */
	public static record F64x2_Promote_Low_F32x4() implements F64x2_Op, VCVTop_HalfQ_Shape_ZQ {}

}
