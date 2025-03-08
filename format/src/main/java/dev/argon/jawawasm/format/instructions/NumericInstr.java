package dev.argon.jawawasm.format.instructions;

/**
 * A WebAssembly numeric instruction.
 */
public sealed interface NumericInstr extends Instr {
	/**
	 * A 32-bit or 64-bit size to compact common instructions.
	 */
	public static enum NumSize {
		/**
		 * 32-bit
		 */
		_32,

		/**
		 * 64-bit
		 */
		_64,
	}


	/**
	 * WebAssembly `i32.const` instruction
	 * @param value The constant value.
	 */
	public static record I32_Const(int value) implements NumericInstr {}

	/**
	 * WebAssembly `i64.const` instruction
	 * @param value The constant value.
	 */
	public static record I64_Const(long value) implements NumericInstr {}

	/**
	 * WebAssembly `f32.const` instruction
	 * @param value The constant value.
	 */
	public static record F32_Const(float value) implements NumericInstr {}

	/**
	 * WebAssembly `f64.const` instruction
	 * @param value The constant value.
	 */
	public static record F64_Const(double value) implements NumericInstr {}


	/**
	 * WebAssembly integer unary operation
	 */
	public static enum IUnOp {
		/**
		 * Count Leading Zeroes
		 */
		CLZ,

		/**
		 * Count Trailing Zeroes
		 */
		CTZ,

		/**
		 * Count non-zero bits
		 */
		POPCNT,
	}


	/**
	 * WebAssembly `inn.iunop` instruction
	 * @param size The size of the number (nn).
	 * @param op The operation (iunop).
	 */
	public static record Inn_IUnOp(NumSize size, IUnOp op) implements NumericInstr {}


	/**
	 * WebAssembly floating point unary operation.
	 */
	public static enum FUnOp {
		/**
		 * Absolute Value
		 */
		ABS,

		/**
		 * Negate
		 */
		NEG,

		/**
		 * Square Root
		 */
		SQRT,

		/**
		 * Round towards +inf
		 */
		CEIL,

		/**
		 * Round towards -inf
		 */
		FLOOR,

		/**
		 * Round towards 0
		 */
		TRUNC,

		/**
		 * Round towards nearest
		 */
		NEAREST,
	}

	/**
	 * WebAssembly `fnn.funop` instruction
	 * @param size The size of the number (nn).
	 * @param op The operation (funop).
	 */
	public static record Fnn_FUnOp(NumSize size, FUnOp op) implements NumericInstr {}



	/**
	 * WebAssembly integer binary operation.
	 */
	public static enum IBinOp {
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
		 * / (unsigned)
		 */
		DIV_U,
		/**
		 * / (signed)
		 */
		DIV_S,
		/**
		 * Remainder (unsigned)
		 */
		REM_U,
		/**
		 * Remainder (signed)
		 */
		REM_S,

		/**
		 * Bitwise and
		 */
		AND,
		/**
		 * Bitwise or
		 */
		OR,
		/**
		 * Bitwise xor
		 */
		XOR,
		/**
		 * Bit shift left
		 */
		SHL,
		/**
		 * Bit shift right (unsigned)
		 */
		SHR_U,
		/**
		 * Bit shift right (signed)
		 */
		SHR_S,
		/**
		 * Rotate left
		 */
		ROTL,
		/**
		 * Rotate right
		 */
		ROTR,
	}

	/**
	 * WebAssembly `inn.ibinop` instruction
	 * @param size The size of the number (nn).
	 * @param op The operation (ibinop).
	 */
	public static record Inn_IBinOp(NumSize size, IBinOp op) implements NumericInstr {}



	/**
	 * WebAssembly floating point binary operation.
	 */
	public static enum FBinOp {
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
		 * Minimum
		 */
		MIN,
		/**
		 * Maximum
		 */
		MAX,
		/**
		 * Copy sign
		 */
		COPYSIGN,
	}

	/**
	 * WebAssembly `fnn.fbinop` instruction
	 * @param size The size of the number (nn).
	 * @param op The operation (fbinop).
	 */
	public static record Fnn_FBinOp(NumSize size, FBinOp op) implements NumericInstr {}



	/**
	 * WebAssembly integer test operation.
	 */
	public static enum ITestOp {
		/**
		 * = 0
		 */
		EQZ,
	}

	/**
	 * WebAssembly `inn.itestop` instruction
	 * @param size The size of the number (nn).
	 * @param op The operation (itestop).
	 */
	public static record Inn_ITestOp(NumSize size, ITestOp op) implements NumericInstr {}


	/**
	 * WebAssembly integer relational operation.
	 */
	public static enum IRelOp {
		/**
		 * =
		 */
		EQ,
		/**
		 * !=
		 */
		NE,
		/**
		 * &lt; (unsigned)
		 */
		LT_U,
		/**
		 * &lt; (signed)
		 */
		LT_S,
		/**
		 * > (unsigned)
		 */
		GT_U,
		/**
		 * > (signed)
		 */
		GT_S,
		/**
		 * &lt;= (unsigned)
		 */
		LE_U,
		/**
		 * &lt;= (signed)
		 */
		LE_S,
		/**
		 * >= (unsigned)
		 */
		GE_U,
		/**
		 * >= (signed)
		 */
		GE_S,
	}

	/**
	 * WebAssembly `inn.irelop` instruction
	 * @param size The size of the number (nn).
	 * @param op The operation (irelop).
	 */
	public static record Inn_IRelOp(NumSize size, IRelOp op) implements NumericInstr {}


	/**
	 * WebAssembly floating point relational operation.
	 */
	public static enum FRelOp {
		/**
		 * =
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
	 * WebAssembly `fnn.frelop` instruction
	 * @param size The size of the number (nn).
	 * @param op The operation (frelop).
	 */
	public static record Fnn_FRelOp(NumSize size, FRelOp op) implements NumericInstr {}


	/**
	 * WebAssembly `inn.extend8_s` instruction
	 * @param size The size of the number (nn).
	 */
	public static record Inn_Extend8_S(NumSize size) implements NumericInstr {}

	/**
	 * WebAssembly `inn.extend16_s` instruction
	 * @param size The size of the number (nn).
	 */
	public static record Inn_Extend16_S(NumSize size) implements NumericInstr {}

	/**
	 * WebAssembly `i32.extend32_s` instruction
	 */
	public static record I64_Extend32_S() implements NumericInstr {}


	/**
	 * WebAssembly `i32.wrap_i64` instruction
	 */
	public static record I32_Wrap_I64() implements NumericInstr {}

	/**
	 * WebAssembly `i64.extend_i32_s` instruction
	 */
	public static record I64_Extend_I32_S() implements NumericInstr {}

	/**
	 * WebAssembly `i64.extend_i32_u` instruction
	 */
	public static record I64_Extend_I32_U() implements NumericInstr {}

	/**
	 * WebAssembly `inn.trunc_fmm_s` instruction
	 * @param intSize The size of the int (nn).
	 * @param floatSize The size of the float (nn).
	 */
	public static record Inn_Trunc_Fmm_S(NumSize intSize, NumSize floatSize) implements NumericInstr {}

	/**
	 * WebAssembly `inn.trunc_fmm_u` instruction
	 * @param intSize The size of the int (nn).
	 * @param floatSize The size of the float (nn).
	 */
	public static record Inn_Trunc_Fmm_U(NumSize intSize, NumSize floatSize) implements NumericInstr {}

	/**
	 * WebAssembly `inn.trunc_sat_fmm_s` instruction
	 * @param intSize The size of the int (nn).
	 * @param floatSize The size of the float (nn).
	 */
	public static record Inn_Trunc_Sat_Fmm_S(NumSize intSize, NumSize floatSize) implements NumericInstr {}

	/**
	 * WebAssembly `inn.trunc_sat_fmm_s` instruction
	 * @param intSize The size of the int (nn).
	 * @param floatSize The size of the float (nn).
	 */
	public static record Inn_Trunc_Sat_Fmm_U(NumSize intSize, NumSize floatSize) implements NumericInstr {}


	/**
	 * WebAssembly `f32.demote_f64` instruction
	 */
	public static record F32_Demote_F64() implements NumericInstr {}

	/**
	 * WebAssembly `f64.promote_f32` instruction
	 */
	public static record F64_Promote_F32() implements NumericInstr {}


	/**
	 * WebAssembly `fnn.convert_fmm_s` instruction
	 * @param floatSize The size of the float (nn).
	 * @param intSize The size of the int (nn).
	 */
	public static record Fnn_Convert_Imm_S(NumSize floatSize, NumSize intSize) implements NumericInstr {}

	/**
	 * WebAssembly `fnn.convert_fmm_u` instruction
	 * @param floatSize The size of the float (nn).
	 * @param intSize The size of the int (nn).
	 */
	public static record Fnn_Convert_Imm_U(NumSize floatSize, NumSize intSize) implements NumericInstr {}


	/**
	 * WebAssembly `inn.reinterpret_fnn` instruction
	 * @param size The size of the number (nn).
	 */
	public static record Inn_Reinterpret_Fnn(NumSize size) implements NumericInstr {}

	/**
	 * WebAssembly `fnn.reinterpret_fnn` instruction
	 * @param size The size of the number (nn).
	 */
	public static record Fnn_Reinterpret_Inn(NumSize size) implements NumericInstr {}


}
