package dev.argon.jawawasm.runtime;

/**
 * A 128-bit vector value.
 * @param b0 Bit 0
 * @param b1 Bit 1
 * @param b2 Bit 2
 * @param b3 Bit 3
 * @param b4 Bit 4
 * @param b5 Bit 5
 * @param b6 Bit 6
 * @param b7 Bit 7
 * @param b8 Bit 8
 * @param b9 Bit 9
 * @param b10 Bit 10
 * @param b11 Bit 11
 * @param b12 Bit 12
 * @param b13 Bit 13
 * @param b14 Bit 14
 * @param b15 Bit 15
 */
public record V128(
		byte b0,
		byte b1,
		byte b2,
		byte b3,
		byte b4,
		byte b5,
		byte b6,
		byte b7,
		byte b8,
		byte b9,
		byte b10,
		byte b11,
		byte b12,
		byte b13,
		byte b14,
		byte b15
) {
	/**
	 * Get an 8-bit lane value.
	 * @param i The lane index.
	 * @return The value.
	 */
	public byte extractLane8(int i) {
		return switch(i) {
			case 0 -> b0;
			case 1 -> b1;
			case 2 -> b2;
			case 3 -> b3;
			case 4 -> b4;
			case 5 -> b5;
			case 6 -> b6;
			case 7 -> b7;
			case 8 -> b8;
			case 9 -> b9;
			case 10 -> b10;
			case 11 -> b11;
			case 12 -> b12;
			case 13 -> b13;
			case 14 -> b14;
			case 15 -> b15;
			default -> throw new IllegalArgumentException();
		};
	}

	/**
	 * Get a 16-bit lane value.
	 * @param i The lane index.
	 * @return The value.
	 */
	public short extractLane16(int i) {
		return switch(i) {
			case 0 -> (short)(Byte.toUnsignedInt(b0) | (Byte.toUnsignedInt(b1) << 8));
			case 1 -> (short)(Byte.toUnsignedInt(b2) | (Byte.toUnsignedInt(b3) << 8));
			case 2 -> (short)(Byte.toUnsignedInt(b4) | (Byte.toUnsignedInt(b5) << 8));
			case 3 -> (short)(Byte.toUnsignedInt(b6) | (Byte.toUnsignedInt(b7) << 8));
			case 4 -> (short)(Byte.toUnsignedInt(b8) | (Byte.toUnsignedInt(b9) << 8));
			case 5 -> (short)(Byte.toUnsignedInt(b10) | (Byte.toUnsignedInt(b11) << 8));
			case 6 -> (short)(Byte.toUnsignedInt(b12) | (Byte.toUnsignedInt(b13) << 8));
			case 7 -> (short)(Byte.toUnsignedInt(b14) | (Byte.toUnsignedInt(b15) << 8));
			default -> throw new IllegalArgumentException();
		};
	}

	/**
	 * Get a 32-bit lane value.
	 * @param i The lane index.
	 * @return The value.
	 */
	public int extractLane32(int i) {
		return switch(i) {
			case 0 -> Byte.toUnsignedInt(b0) | (Byte.toUnsignedInt(b1) << 8) |
					(Byte.toUnsignedInt(b2) << 16) | (Byte.toUnsignedInt(b3) << 24);
			case 1 -> Byte.toUnsignedInt(b4) | (Byte.toUnsignedInt(b5) << 8) |
					(Byte.toUnsignedInt(b6) << 16) | (Byte.toUnsignedInt(b7) << 24);
			case 2 -> Byte.toUnsignedInt(b8) | (Byte.toUnsignedInt(b9) << 8) |
					(Byte.toUnsignedInt(b10) << 16) | (Byte.toUnsignedInt(b11) << 24);
			case 3 -> Byte.toUnsignedInt(b12) | (Byte.toUnsignedInt(b13) << 8) |
					(Byte.toUnsignedInt(b14) << 16) | (Byte.toUnsignedInt(b15) << 24);
			default -> throw new IllegalArgumentException();
		};
	}

	/**
	 * Get a 64-bit lane value.
	 * @param i The lane index.
	 * @return The value.
	 */
	public long extractLane64(int i) {
		return switch(i) {
			case 0 -> Byte.toUnsignedInt(b0) | (Byte.toUnsignedInt(b1) << 8) |
					(Byte.toUnsignedLong(b2) << 16) | (Byte.toUnsignedLong(b3) << 24) |
					(Byte.toUnsignedLong(b4) << 32) | (Byte.toUnsignedLong(b5) << 40) |
					(Byte.toUnsignedLong(b6) << 48) | (Byte.toUnsignedLong(b7) << 56);
			case 1 -> Byte.toUnsignedInt(b8) | (Byte.toUnsignedInt(b9) << 8) |
					(Byte.toUnsignedLong(b10) << 16) | (Byte.toUnsignedLong(b11) << 24) |
					(Byte.toUnsignedLong(b12) << 32) | (Byte.toUnsignedLong(b13) << 40) |
					(Byte.toUnsignedLong(b14) << 48) | (Byte.toUnsignedLong(b15) << 56);
			default -> throw new IllegalArgumentException();
		};
	}

	/**
	 * Get a 32-bit float lane value.
	 * @param i The lane index.
	 * @return The value.
	 */
	public float extractLaneF32(int i) {
		return Float.intBitsToFloat(extractLane32(i));
	}


	/**
	 * Get a 64-bit float lane value.
	 * @param i The lane index.
	 * @return The value.
	 */
	public double extractLaneF64(int i) {
		return Double.longBitsToDouble(extractLane64(i));
	}

	/**
	 * A function to build a V128 from bytes.
	 */
	@FunctionalInterface
	public static interface Build8Function {
		/**
		 * Apply this function.
		 * @param index The index.
		 * @return The value.
		 */
		byte apply(int index);
	}


	/**
	 * Build a V128 from bytes.
	 * @param f The function.
	 * @return The result.
	 */
	public static V128 build8(Build8Function f) {
		return new V128(
				f.apply(0),
				f.apply(1),
				f.apply(2),
				f.apply(3),
				f.apply(4),
				f.apply(5),
				f.apply(6),
				f.apply(7),
				f.apply(8),
				f.apply(9),
				f.apply(10),
				f.apply(11),
				f.apply(12),
				f.apply(13),
				f.apply(14),
				f.apply(15)
		);
	}


	/**
	 * A function to build a V128 from 16-bit values.
	 */
	@FunctionalInterface
	public static interface Build16Function {
		/**
		 * Apply this function.
		 * @param index The index.
		 * @return The value.
		 */
		short apply(int index);
	}

	/**
	 * Build a V128 from 16-bit values.
	 * @param f The function.
	 * @return The result.
	 */
	public static V128 build16(Build16Function f) {
		short n0 = f.apply(0);
		short n1 = f.apply(1);
		short n2 = f.apply(2);
		short n3 = f.apply(3);
		short n4 = f.apply(4);
		short n5 = f.apply(5);
		short n6 = f.apply(6);
		short n7 = f.apply(7);

		return new V128(
				(byte)n0,
				(byte)(n0 >>> 8),
				(byte)n1,
				(byte)(n1 >>> 8),
				(byte)n2,
				(byte)(n2 >>> 8),
				(byte)n3,
				(byte)(n3 >>> 8),
				(byte)n4,
				(byte)(n4 >>> 8),
				(byte)n5,
				(byte)(n5 >>> 8),
				(byte)n6,
				(byte)(n6 >>> 8),
				(byte)n7,
				(byte)(n7 >>> 8)
		);
	}


	/**
	 * A function to build a V128 from 32-bit values.
	 */
	@FunctionalInterface
	public static interface Build32Function {
		/**
		 * Apply this function.
		 * @param index The index.
		 * @return The value.
		 */
		int apply(int index);
	}


	/**
	 * Build a V128 from 32-bit values.
	 * @param f The function.
	 * @return The result.
	 */
	public static V128 build32(Build32Function f) {
		int n0 = f.apply(0);
		int n1 = f.apply(1);
		int n2 = f.apply(2);
		int n3 = f.apply(3);

		return new V128(
				(byte)n0,
				(byte)(n0 >>> 8),
				(byte)(n0 >>> 16),
				(byte)(n0 >>> 24),
				(byte)n1,
				(byte)(n1 >>> 8),
				(byte)(n1 >>> 16),
				(byte)(n1 >>> 24),
				(byte)n2,
				(byte)(n2 >>> 8),
				(byte)(n2 >>> 16),
				(byte)(n2 >>> 24),
				(byte)n3,
				(byte)(n3 >>> 8),
				(byte)(n3 >>> 16),
				(byte)(n3 >>> 24)
		);
	}


	/**
	 * A function to build a V128 from 64-bit values.
	 */
	@FunctionalInterface
	public static interface Build64Function {
		/**
		 * Apply this function.
		 * @param index The index.
		 * @return The value.
		 */
		long apply(int index);
	}


	/**
	 * Build a V128 from 64-bit values.
	 * @param f The function.
	 * @return The result.
	 */
	public static V128 build64(Build64Function f) {
		long n0 = f.apply(0);
		long n1 = f.apply(1);

		return new V128(
				(byte)n0,
				(byte)(n0 >>> 8),
				(byte)(n0 >>> 16),
				(byte)(n0 >>> 24),
				(byte)(n0 >>> 32),
				(byte)(n0 >>> 40),
				(byte)(n0 >>> 48),
				(byte)(n0 >>> 56),
				(byte)n1,
				(byte)(n1 >>> 8),
				(byte)(n1 >>> 16),
				(byte)(n1 >>> 24),
				(byte)(n1 >>> 32),
				(byte)(n1 >>> 40),
				(byte)(n1 >>> 48),
				(byte)(n1 >>> 56)
		);
	}


	/**
	 * A function to build a V128 from 32-bit float values.
	 */
	@FunctionalInterface
	public static interface BuildF32Function {
		/**
		 * Apply this function.
		 * @param index The index.
		 * @return The value.
		 */
		float apply(int index);
	}


	/**
	 * Build a V128 from 32-bit float values.
	 * @param f The function.
	 * @return The result.
	 */
	public static V128 buildF32(BuildF32Function f) {
		return build32(i -> Float.floatToRawIntBits(f.apply(i)));
	}


	/**
	 * A function to build a V128 from 64-bit float values.
	 */
	@FunctionalInterface
	public static interface BuildF64Function {
		/**
		 * Apply this function.
		 * @param index The index.
		 * @return The value.
		 */
		double apply(int index);
	}


	/**
	 * Build a V128 from 64-bit float values.
	 * @param f The function.
	 * @return The result.
	 */
	public static V128 buildF64(BuildF64Function f) {
		return build64(i -> Double.doubleToRawLongBits(f.apply(i)));
	}


	/**
	 * A unary function for 8-bit values.
	 */
	@FunctionalInterface
	public static interface Unary8Function {
		/**
		 * Apply this function.
		 * @param a The operand.
		 * @return The value.
		 */
		byte apply(byte a);
	}

	/**
	 * Apply a unary function for 8-bit values.
	 * @param f The function.
	 * @return The result.
	 */
	public V128 unary8(Unary8Function f) {
		return build8(i -> f.apply(extractLane8(i)));
	}


	/**
	 * A unary function for 16-bit values.
	 */
	@FunctionalInterface
	public static interface Unary16Function {
		/**
		 * Apply this function.
		 * @param a The operand.
		 * @return The value.
		 */
		short apply(short a);
	}


	/**
	 * Apply a unary function for 16-bit values.
	 * @param f The function.
	 * @return The result.
	 */
	public V128 unary16(Unary16Function f) {
		return build16(i -> f.apply(extractLane16(i)));
	}


	/**
	 * A unary function for 32-bit values.
	 */
	@FunctionalInterface
	public static interface Unary32Function {
		/**
		 * Apply this function.
		 * @param a The operand.
		 * @return The value.
		 */
		int apply(int a);
	}


	/**
	 * Apply a unary function for 32-bit values.
	 * @param f The function.
	 * @return The result.
	 */
	public V128 unary32(Unary32Function f) {
		return build32(i -> f.apply(extractLane32(i)));
	}


	/**
	 * A unary function for 64-bit values.
	 */
	@FunctionalInterface
	public static interface Unary64Function {
		/**
		 * Apply this function.
		 * @param a The operand.
		 * @return The value.
		 */
		long apply(long a);
	}


	/**
	 * Apply a unary function for 64-bit values.
	 * @param f The function.
	 * @return The result.
	 */
	public V128 unary64(Unary64Function f) {
		return build64(i -> f.apply(extractLane64(i)));
	}


	/**
	 * A unary function for 32-bit float values.
	 */
	@FunctionalInterface
	public static interface UnaryF32Function {
		/**
		 * Apply this function.
		 * @param a The operand.
		 * @return The value.
		 */
		float apply(float a);
	}


	/**
	 * Apply a unary function for 32-bit float values.
	 * @param f The function.
	 * @return The result.
	 */
	public V128 unaryF32(UnaryF32Function f) {
		return buildF32(i -> f.apply(extractLaneF32(i)));
	}


	/**
	 * A unary function for 64-bit float values.
	 */
	@FunctionalInterface
	public static interface UnaryF64Function {
		/**
		 * Apply this function.
		 * @param a The operand.
		 * @return The value.
		 */
		double apply(double a);
	}


	/**
	 * Apply a unary function for 64-bit float values.
	 * @param f The function.
	 * @return The result.
	 */
	public V128 unaryF64(UnaryF64Function f) {
		return buildF64(i -> f.apply(extractLaneF64(i)));
	}


	/**
	 * A binary function for 8-bit values.
	 */
	@FunctionalInterface
	public static interface Binary8Function {
		/**
		 * Apply this function.
		 * @param a The first operand.
		 * @param b The second operand.
		 * @return The value.
		 */
		byte apply(byte a, byte b);
	}

	/**
	 * Apply a binary function for 8-bit values.
	 * @param other The other vector.
	 * @param f The function.
	 * @return The result.
	 */
	public V128 binary8(V128 other, Binary8Function f) {
		return build8(i -> f.apply(extractLane8(i), other.extractLane8(i)));
	}


	/**
	 * A binary function for 16-bit values.
	 */
	@FunctionalInterface
	public static interface Binary16Function {
		/**
		 * Apply this function.
		 * @param a The first operand.
		 * @param b The second operand.
		 * @return The value.
		 */
		short apply(short a, short b);
	}

	/**
	 * Apply a binary function for 16-bit values.
	 * @param other The other vector.
	 * @param f The function.
	 * @return The result.
	 */
	public V128 binary16(V128 other, Binary16Function f) {
		return build16(i -> f.apply(extractLane16(i), other.extractLane16(i)));
	}


	/**
	 * A binary function for 32-bit values.
	 */
	@FunctionalInterface
	public static interface Binary32Function {
		/**
		 * Apply this function.
		 * @param a The first operand.
		 * @param b The second operand.
		 * @return The value.
		 */
		int apply(int a, int b);
	}

	/**
	 * Apply a binary function for 32-bit values.
	 * @param other The other vector.
	 * @param f The function.
	 * @return The result.
	 */
	public V128 binary32(V128 other, Binary32Function f) {
		return build32(i -> f.apply(extractLane32(i), other.extractLane32(i)));
	}


	/**
	 * A binary function for 64-bit values.
	 */
	@FunctionalInterface
	public static interface Binary64Function {
		/**
		 * Apply this function.
		 * @param a The first operand.
		 * @param b The second operand.
		 * @return The value.
		 */
		long apply(long a, long b);
	}

	/**
	 * Apply a binary function for 64-bit values.
	 * @param other The other vector.
	 * @param f The function.
	 * @return The result.
	 */
	public V128 binary64(V128 other, Binary64Function f) {
		return build64(i -> f.apply(extractLane64(i), other.extractLane64(i)));
	}


	/**
	 * A binary function for 32-bit float values.
	 */
	@FunctionalInterface
	public static interface BinaryF32Function {
		/**
		 * Apply this function.
		 * @param a The first operand.
		 * @param b The second operand.
		 * @return The value.
		 */
		float apply(float a, float b);
	}

	/**
	 * Apply a binary function for 32-bit float values.
	 * @param other The other vector.
	 * @param f The function.
	 * @return The result.
	 */
	public V128 binaryF32(V128 other, BinaryF32Function f) {
		return buildF32(i -> f.apply(extractLaneF32(i), other.extractLaneF32(i)));
	}


	/**
	 * A binary function for 64-bit float values.
	 */
	@FunctionalInterface
	public static interface BinaryF64Function {
		/**
		 * Apply this function.
		 * @param a The first operand.
		 * @param b The second operand.
		 * @return The value.
		 */
		double apply(double a, double b);
	}

	/**
	 * Apply a binary function for 64-bit float values.
	 * @param other The other vector.
	 * @param f The function.
	 * @return The result.
	 */
	public V128 binaryF64(V128 other, BinaryF64Function f) {
		return buildF64(i -> f.apply(extractLaneF64(i), other.extractLaneF64(i)));
	}


	/**
	 * A ternary function for 8-bit values.
	 */
	@FunctionalInterface
	public static interface Ternary8Function {
		/**
		 * Apply this function.
		 * @param a The first operand.
		 * @param b The second operand.
		 * @param c The third operand.
		 * @return The value.
		 */
		byte apply(byte a, byte b, byte c);
	}

	/**
	 * Apply a ternary function for 8-bit values.
	 * @param second The second vector.
	 * @param third The third vector.
	 * @param f The function.
	 * @return The result.
	 */
	public V128 ternary8(V128 second, V128 third, Ternary8Function f) {
		return build8(i -> f.apply(extractLane8(i), second.extractLane8(i), third.extractLane8(i)));
	}



	/**
	 * A ternary function for 32-bit float values.
	 */
	@FunctionalInterface
	public static interface TernaryF32Function {
		/**
		 * Apply this function.
		 * @param a The first operand.
		 * @param b The second operand.
		 * @param c The third operand.
		 * @return The value.
		 */
		float apply(float a, float b, float c);
	}

	/**
	 * Apply a ternary function for 32-bit float values.
	 * @param second The second operand.
	 * @param third The third operand.
	 * @param f The function.
	 * @return The result.
	 */
	public V128 ternaryF32(V128 second, V128 third, TernaryF32Function f) {
		return buildF32(i -> f.apply(extractLaneF32(i), second.extractLaneF32(i), third.extractLaneF32(i)));
	}

	/**
	 * A ternary function for 64-bit float values.
	 */
	@FunctionalInterface
	public static interface TernaryF64Function {
		/**
		 * Apply this function.
		 * @param a The first operand.
		 * @param b The second operand.
		 * @param c The third operand.
		 * @return The value.
		 */
		double apply(double a, double b, double c);
	}

	/**
	 * Apply a ternary function for 64-bit float values.
	 * @param second The second operand.
	 * @param third The third operand.
	 * @param f The function.
	 * @return The result.
	 */
	public V128 ternaryF64(V128 second, V128 third, TernaryF64Function f) {
		return buildF64(i -> f.apply(extractLaneF64(i), second.extractLaneF64(i), third.extractLaneF64(i)));
	}


	/**
	 * Check for true values.
	 * @return true iff any of the bits are set
	 */
	public boolean anyTrue() {
		for(int i = 0; i < 16; ++i) {
			if(extractLane8(i) != 0) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Narrow two vectors with 16-bit lanes to a single vector with 8-bit lanes using signed operations.
	 * @param b The second vector
	 * @return The result.
	 */
	public V128 narrow16To8Signed(V128 b) {
		return build8(i -> Util.narrowS16I8(i < 8 ? extractLane16(i) : b.extractLane16(i - 8)));
	}

	/**
	 * Narrow two vectors with 16-bit lanes to a single vector with 8-bit lanes using unsigned operations.
	 * @param b The second vector
	 * @return The result.
	 */
	public V128 narrow16To8Unsigned(V128 b) {
		return build8(i -> Util.narrowU16I8(i < 8 ? extractLane16(i) : b.extractLane16(i - 8)));
	}

	/**
	 * Narrow two vectors with 32-bit lanes to a single vector with 16-bit lanes using signed operations.
	 * @param b The second vector
	 * @return The result.
	 */
	public V128 narrow32To16Signed(V128 b) {
		return build16(i -> Util.narrowS32I16(i < 4 ? extractLane32(i) : b.extractLane32(i - 4)));
	}

	/**
	 * Narrow two vectors with 32-bit lanes to a single vector with 16-bit lanes using unsigned operations.
	 * @param b The second vector
	 * @return The result.
	 */
	public V128 narrow32To16Unsigned(V128 b) {
		return build16(i -> Util.narrowU32I16(i < 4 ? extractLane32(i) : b.extractLane32(i - 4)));
	}

	/**
	 * Extend the low half of the vector from 8-bit unsigned to 16-bit
	 * @return The result.
	 */
	public V128 extendLowU8To16() {
		return V128.build16(i -> (short)Byte.toUnsignedInt(extractLane8(i)));
	}

	/**
	 * Extend the low half of the vector from 8-bit signed to 16-bit
	 * @return The result.
	 */
	public V128 extendLowS8To16() {
		return V128.build16(this::extractLane8);
	}

	/**
	 * Extend the high half of the vector from 8-bit unsigned to 16-bit
	 * @return The result.
	 */
	public V128 extendHighU8To16() {
		return V128.build16(i -> (short)Byte.toUnsignedInt(extractLane8(i + 8)));
	}

	/**
	 * Extend the high half of the vector from 8-bit signed to 16-bit
	 * @return The result.
	 */
	public V128 extendHighS8To16() {
		return V128.build16(i -> extractLane8(i + 8));
	}


	/**
	 * POPCNT operation for 8-bit.
	 * @return The result.
	 */
	public V128 popcnt8() {
		return unary8(n0 -> (byte)Integer.bitCount(Byte.toUnsignedInt(n0)));
	}

	/**
	 * Swizzle WebAssembly operation for 8-bit.
	 * @param indexes The indexes.
	 * @return The result.
	 */
	public V128 swizzle8(V128 indexes) {
		return build8(i -> {
			int index = Byte.toUnsignedInt(indexes.extractLane8(i));
			if(index < 16) {
				return extractLane8(index);
			}
			else {
				return (byte)0;
			}
		});
	}

	/**
	 * WebAssembly shuffle operation for 8-bit.
	 * @param b The second set of values.
	 * @param laneIndexes The laneIndexes.
	 * @return The result.
	 */
	public V128 shuffle8(V128 b, V128 laneIndexes) {
		V128 a = this;
		return build8(i -> {
			int index = laneIndexes.extractLane8(i);
			if(index < 16) {
				return a.extractLane8(index);
			}
			else {
				return b.extractLane8(index - 16);
			}
		});
	}


	/**
	 * Splat WebAssembly operation for 8-bit.
	 * @param value The value.
	 * @return The result.
	 */
	public static V128 splat8(byte value) {
		return build8(i -> value);
	}

	/**
	 * Splat WebAssembly operation for 8-bit.
	 * @param value The value.
	 * @return The result.
	 */
	public static V128 splat16(short value) {
		return build16(i -> value);
	}

	/**
	 * Splat WebAssembly operation for 32-bit.
	 * @param value The value.
	 * @return The result.
	 */
	public static V128 splat32(int value) {
		return build32(i -> value);
	}

	/**
	 * Splat WebAssembly operation for 64-bit.
	 * @param value The value.
	 * @return The result.
	 */
	public static V128 splat64(long value) {
		return build64(i -> value);
	}

	/**
	 * Splat WebAssembly operation for 32-bit float.
	 * @param value The value.
	 * @return The result.
	 */
	public static V128 splatF32(float value) {
		return buildF32(i -> value);
	}

	/**
	 * Splat WebAssembly operation for 64-bit float.
	 * @param value The value.
	 * @return The result.
	 */
	public static V128 splatF64(double value) {
		return buildF64(i -> value);
	}

	/**
	 * Replace an 8-bit lane.
	 * @param index The index.
	 * @param value The value.
	 * @return The result.
	 */
	public V128 replaceLane8(byte value, int index) {
		return build8(i -> i == index ? value : extractLane8(i));
	}


	/**
	 * Replace a 16-bit lane.
	 * @return The result.
	 */
	public V128 replaceLane16(short value, int index) {
		return build16(i -> i == index ? value : extractLane16(i));
	}

	/**
	 * Replace a 32-bit lane.
	 * @param value The value.
	 * @param index The index.
	 * @return The result.
	 */
	public V128 replaceLane32(int value, int index) {
		return build32(i -> i == index ? value : extractLane32(i));
	}

	/**
	 * Replace a 64-bit lane.
	 * @param value The value.
	 * @param index The index.
	 * @return The result.
	 */
	public V128 replaceLane64(long value, int index) {
		return build64(i -> i == index ? value : extractLane64(i));
	}

	/**
	 * Replace a 32-bit float lane.
	 * @param value The value.
	 * @param index The index.
	 * @return The result.
	 */
	public V128 replaceLaneF32(float value, int index) {
		return buildF32(i -> i == index ? value : extractLaneF32(i));
	}

	/**
	 * Replace a 64-bit float lane.
	 * @param value The value.
	 * @param index The index.
	 * @return The result.
	 */
	public V128 replaceLaneF64(double value, int index) {
		return buildF64(i -> i == index ? value : extractLaneF64(i));
	}

	/**
	 * Check if all 8-bit values are true.
	 * @return true iff all 8-bit values are true.
	 */
	public boolean allTrue8() {
		for(int i = 0; i < 16; ++i) {
			if(extractLane8(i) == 0) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Check if all 16-bit values are true.
	 * @return true iff all 16-bit values are true.
	 */
	public boolean allTrue16() {
		for(int i = 0; i < 8; ++i) {
			if(extractLane16(i) == 0) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Check if all 32-bit values are true.
	 * @return true iff all 32-bit values are true.
	 */
	public boolean allTrue32() {
		for(int i = 0; i < 4; ++i) {
			if(extractLane32(i) == 0) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Check if all 64-bit values are true.
	 * @return true iff all 64-bit values are true.
	 */
	public boolean allTrue64() {
		for(int i = 0; i < 2; ++i) {
			if(extractLane64(i) == 0) {
				return false;
			}
		}

		return true;
	}

	/**
	 * WebAssembly bitmask operation for 8-bit values.
	 * @return The result.
	 */
	public int bitmask8() {
		int result = 0;
		for(int i = 0; i < 16; ++i) {
			if(extractLane8(i) < 0) {
				result |= 1 << i;
			}
		}
		return result;
	}


	/**
	 * WebAssembly bitmask operation for 16-bit values.
	 * @return The result.
	 */
	public int bitmask16() {
		int result = 0;
		for(int i = 0; i < 8; ++i) {
			if(extractLane16(i) < 0) {
				result |= 1 << i;
			}
		}
		return result;
	}


	/**
	 * WebAssembly bitmask operation for 32-bit values.
	 * @return The result.
	 */
	public int bitmask32() {
		int result = 0;
		for(int i = 0; i < 4; ++i) {
			if(extractLane32(i) < 0) {
				result |= 1 << i;
			}
		}
		return result;
	}


	/**
	 * WebAssembly bitmask operation for 64-bit values.
	 * @return The result.
	 */
	public int bitmask64() {
		int result = 0;
		for(int i = 0; i < 2; ++i) {
			if(extractLane64(i) < 0) {
				result |= 1 << i;
			}
		}
		return result;
	}

	public V128 q15mulrSatS(V128 b) {
		return binary16(b, (n0, n1) -> Util.narrowS32I16((n0 * n1 + (1 << 14)) >> 15));
	}


	/**
	 * Compares two V128 vectors for equality across 16 lanes of 8-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if equal, 0 if not equal
	 */
	public V128 equals8(V128 b) {
		return binary8(b, (n0, n1) -> n0 == n1 ? (byte)-1 : (byte)0);
	}

	/**
	 * Compares two V128 vectors for inequality across 16 lanes of 8-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if not equal, 0 if equal
	 */
	public V128 notEquals8(V128 b) {
		return binary8(b, (n0, n1) -> n0 != n1 ? (byte)-1 : (byte)0);
	}

	/**
	 * Performs signed less-than comparison across 16 lanes of 8-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than, 0 otherwise
	 */
	public V128 lessThanSigned8(V128 b) {
		return binary8(b, (n0, n1) -> n0 < n1 ? (byte)-1 : (byte)0);
	}

	/**
	 * Performs unsigned less-than comparison across 16 lanes of 8-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than, 0 otherwise
	 */
	public V128 lessThanUnsigned8(V128 b) {
		return binary8(b, (n0, n1) ->
			Byte.compareUnsigned(n0, n1) < 0 ? (byte)-1 : (byte)0);
	}

	/**
	 * Performs signed greater-than comparison across 16 lanes of 8-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than, 0 otherwise
	 */
	public V128 greaterThanSigned8(V128 b) {
		return binary8(b, (n0, n1) -> n0 > n1 ? (byte)-1 : (byte)0);
	}

	/**
	 * Performs unsigned greater-than comparison across 16 lanes of 8-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than, 0 otherwise
	 */
	public V128 greaterThanUnsigned8(V128 b) {
		return binary8(b, (n0, n1) ->
			Byte.compareUnsigned(n0, n1) > 0 ? (byte)-1 : (byte)0);
	}

	/**
	 * Performs signed less-than-or-equal comparison across 16 lanes of 8-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than or equal, 0 otherwise
	 */
	public V128 lessThanOrEqualSigned8(V128 b) {
		return binary8(b, (n0, n1) -> n0 <= n1 ? (byte)-1 : (byte)0);
	}

	/**
	 * Performs unsigned less-than-or-equal comparison across 16 lanes of 8-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than or equal, 0 otherwise
	 */
	public V128 lessThanOrEqualUnsigned8(V128 b) {
		return binary8(b, (n0, n1) ->
			Byte.compareUnsigned(n0, n1) <= 0 ? (byte)-1 : (byte)0);
	}

	/**
	 * Performs signed greater-than-or-equal comparison across 16 lanes of 8-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than or equal, 0 otherwise
	 */
	public V128 greaterThanOrEqualSigned8(V128 b) {
		return binary8(b, (n0, n1) -> n0 >= n1 ? (byte)-1 : (byte)0);
	}

	/**
	 * Performs unsigned greater-than-or-equal comparison across 16 lanes of 8-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than or equal, 0 otherwise
	 */
	public V128 greaterThanOrEqualUnsigned8(V128 b) {
		return binary8(b, (n0, n1) ->
			Byte.compareUnsigned(n0, n1) >= 0 ? (byte)-1 : (byte)0);
	}
	/**
	 * Compares two V128 vectors for equality across 8 lanes of 16-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if equal, 0 if not equal
	 */
	public V128 equals16(V128 b) {
		return binary16(b, (n0, n1) -> n0 == n1 ? (short)-1 : (short)0);
	}

	/**
	 * Compares two V128 vectors for inequality across 8 lanes of 16-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if not equal, 0 if equal
	 */
	public V128 notEquals16(V128 b) {
		return binary16(b, (n0, n1) -> n0 != n1 ? (short)-1 : (short)0);
	}

	/**
	 * Performs signed less-than comparison across 8 lanes of 16-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than, 0 otherwise
	 */
	public V128 lessThanSigned16(V128 b) {
		return binary16(b, (n0, n1) -> n0 < n1 ? (short)-1 : (short)0);
	}

	/**
	 * Performs unsigned less-than comparison across 8 lanes of 16-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than, 0 otherwise
	 */
	public V128 lessThanUnsigned16(V128 b) {
		return binary16(b, (n0, n1) ->
			Short.compareUnsigned(n0, n1) < 0 ? (short)-1 : (short)0);
	}

	/**
	 * Performs signed greater-than comparison across 8 lanes of 16-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than, 0 otherwise
	 */
	public V128 greaterThanSigned16(V128 b) {
		return binary16(b, (n0, n1) -> n0 > n1 ? (short)-1 : (short)0);
	}

	/**
	 * Performs unsigned greater-than comparison across 8 lanes of 16-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than, 0 otherwise
	 */
	public V128 greaterThanUnsigned16(V128 b) {
		return binary16(b, (n0, n1) ->
			Short.compareUnsigned(n0, n1) > 0 ? (short)-1 : (short)0);
	}

	/**
	 * Performs signed less-than-or-equal comparison across 8 lanes of 16-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than or equal, 0 otherwise
	 */
	public V128 lessThanOrEqualSigned16(V128 b) {
		return binary16(b, (n0, n1) -> n0 <= n1 ? (short)-1 : (short)0);
	}

	/**
	 * Performs unsigned less-than-or-equal comparison across 8 lanes of 16-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than or equal, 0 otherwise
	 */
	public V128 lessThanOrEqualUnsigned16(V128 b) {
		return binary16(b, (n0, n1) ->
			Short.compareUnsigned(n0, n1) <= 0 ? (short)-1 : (short)0);
	}

	/**
	 * Performs signed greater-than-or-equal comparison across 8 lanes of 16-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than or equal, 0 otherwise
	 */
	public V128 greaterThanOrEqualSigned16(V128 b) {
		return binary16(b, (n0, n1) -> n0 >= n1 ? (short)-1 : (short)0);
	}

	/**
	 * Performs unsigned greater-than-or-equal comparison across 8 lanes of 16-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than or equal, 0 otherwise
	 */
	public V128 greaterThanOrEqualUnsigned16(V128 b) {
		return binary16(b, (n0, n1) ->
			Short.compareUnsigned(n0, n1) >= 0 ? (short)-1 : (short)0);
	}
	
	/**
	 * Compares two V128 vectors for equality across 4 lanes of 32-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if equal, 0 if not equal
	 */
	public V128 equals32(V128 b) {
		return binary32(b, (n0, n1) -> n0 == n1 ? -1 : 0);
	}

	/**
	 * Compares two V128 vectors for inequality across 4 lanes of 32-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if not equal, 0 if equal
	 */
	public V128 notEquals32(V128 b) {
		return binary32(b, (n0, n1) -> n0 != n1 ? -1 : 0);
	}

	/**
	 * Performs signed less-than comparison across 4 lanes of 32-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than, 0 otherwise
	 */
	public V128 lessThanSigned32(V128 b) {
		return binary32(b, (n0, n1) -> n0 < n1 ? -1 : 0);
	}

	/**
	 * Performs unsigned less-than comparison across 4 lanes of 32-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than, 0 otherwise
	 */
	public V128 lessThanUnsigned32(V128 b) {
		return binary32(b, (n0, n1) -> Integer.compareUnsigned(n0, n1) < 0 ? -1 : 0);
	}

	/**
	 * Performs signed greater-than comparison across 4 lanes of 32-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than, 0 otherwise
	 */
	public V128 greaterThanSigned32(V128 b) {
		return binary32(b, (n0, n1) -> n0 > n1 ? -1 : 0);
	}

	/**
	 * Performs unsigned greater-than comparison across 4 lanes of 32-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than, 0 otherwise
	 */
	public V128 greaterThanUnsigned32(V128 b) {
		return binary32(b, (n0, n1) -> Integer.compareUnsigned(n0, n1) > 0 ? -1 : 0);
	}

	/**
	 * Performs signed less-than-or-equal comparison across 4 lanes of 32-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than or equal, 0 otherwise
	 */
	public V128 lessThanOrEqualSigned32(V128 b) {
		return binary32(b, (n0, n1) -> n0 <= n1 ? -1 : 0);
	}

	/**
	 * Performs unsigned less-than-or-equal comparison across 4 lanes of 32-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than or equal, 0 otherwise
	 */
	public V128 lessThanOrEqualUnsigned32(V128 b) {
		return binary32(b, (n0, n1) -> Integer.compareUnsigned(n0, n1) <= 0 ? -1 : 0);
	}

	/**
	 * Performs signed greater-than-or-equal comparison across 4 lanes of 32-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than or equal, 0 otherwise
	 */
	public V128 greaterThanOrEqualSigned32(V128 b) {
		return binary32(b, (n0, n1) -> n0 >= n1 ? -1 : 0);
	}

	/**
	 * Performs unsigned greater-than-or-equal comparison across 4 lanes of 32-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than or equal, 0 otherwise
	 */
	public V128 greaterThanOrEqualUnsigned32(V128 b) {
		return binary32(b, (n0, n1) -> Integer.compareUnsigned(n0, n1) >= 0 ? -1 : 0);
	}

	/**
	 * Compares two V128 vectors for equality across 2 lanes of 64-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if equal, 0 if not equal
	 */
	public V128 equals64(V128 b) {
		return binary64(b, (n0, n1) -> n0 == n1 ? -1L : 0L);
	}

	/**
	 * Compares two V128 vectors for inequality across 2 lanes of 64-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if not equal, 0 if equal
	 */
	public V128 notEquals64(V128 b) {
		return binary64(b, (n0, n1) -> n0 != n1 ? -1L : 0L);
	}

	/**
	 * Performs signed less-than comparison across 2 lanes of 64-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than, 0 otherwise
	 */
	public V128 lessThanSigned64(V128 b) {
		return binary64(b, (n0, n1) -> n0 < n1 ? -1L : 0L);
	}

	/**
	 * Performs unsigned less-than comparison across 2 lanes of 64-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than, 0 otherwise
	 */
	public V128 lessThanUnsigned64(V128 b) {
		return binary64(b, (n0, n1) -> Long.compareUnsigned(n0, n1) < 0 ? -1L : 0L);
	}

	/**
	 * Performs signed greater-than comparison across 2 lanes of 64-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than, 0 otherwise
	 */
	public V128 greaterThanSigned64(V128 b) {
		return binary64(b, (n0, n1) -> n0 > n1 ? -1L : 0L);
	}

	/**
	 * Performs unsigned greater-than comparison across 2 lanes of 64-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than, 0 otherwise
	 */
	public V128 greaterThanUnsigned64(V128 b) {
		return binary64(b, (n0, n1) -> Long.compareUnsigned(n0, n1) > 0 ? -1L : 0L);
	}

	/**
	 * Performs signed less-than-or-equal comparison across 2 lanes of 64-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than or equal, 0 otherwise
	 */
	public V128 lessThanOrEqualSigned64(V128 b) {
		return binary64(b, (n0, n1) -> n0 <= n1 ? -1L : 0L);
	}

	/**
	 * Performs unsigned less-than-or-equal comparison across 2 lanes of 64-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if less than or equal, 0 otherwise
	 */
	public V128 lessThanOrEqualUnsigned64(V128 b) {
		return binary64(b, (n0, n1) -> Long.compareUnsigned(n0, n1) <= 0 ? -1L : 0L);
	}

	/**
	 * Performs signed greater-than-or-equal comparison across 2 lanes of 64-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than or equal, 0 otherwise
	 */
	public V128 greaterThanOrEqualSigned64(V128 b) {
		return binary64(b, (n0, n1) -> n0 >= n1 ? -1L : 0L);
	}

	/**
	 * Performs unsigned greater-than-or-equal comparison across 2 lanes of 64-bit values.
	 * @param b The second V128 vector to compare against
	 * @return A new V128 where each lane contains -1 if greater than or equal, 0 otherwise
	 */
	public V128 greaterThanOrEqualUnsigned64(V128 b) {
		return binary64(b, (n0, n1) -> Long.compareUnsigned(n0, n1) >= 0 ? -1L : 0L);
	}
	
	/**
	 * Computes the absolute value of each 8-bit lane in the V128 vector.
	 * @return A new V128 with absolute values in each of the 16 lanes
	 */
	public V128 abs8() {
		return unary8(n -> (byte)Math.abs(n));
	}

	/**
	 * Negates each 8-bit lane in the V128 vector.
	 * @return A new V128 with negated values in each of the 16 lanes
	 */
	public V128 neg8() {
		return unary8(n -> (byte)(-n));
	}

	/**
	 * Computes the absolute value of each 16-bit lane in the V128 vector.
	 * @return A new V128 with absolute values in each of the 8 lanes
	 */
	public V128 abs16() {
		return unary16(n -> (short)Math.abs(n));
	}

	/**
	 * Negates each 16-bit lane in the V128 vector.
	 * @return A new V128 with negated values in each of the 8 lanes
	 */
	public V128 neg16() {
		return unary16(n -> (short)(-n));
	}

	/**
	 * Computes the absolute value of each 32-bit lane in the V128 vector.
	 * @return A new V128 with absolute values in each of the 4 lanes
	 */
	public V128 abs32() {
		return unary32(Math::abs);
	}

	/**
	 * Negates each 32-bit lane in the V128 vector.
	 * @return A new V128 with negated values in each of the 4 lanes
	 */
	public V128 neg32() {
		return unary32(n -> -n);
	}

	// Lane size 64 (i64x2)

	/**
	 * Computes the absolute value of each 64-bit lane in the V128 vector.
	 * @return A new V128 with absolute values in each of the 2 lanes
	 */
	public V128 abs64() {
		return unary64(Math::abs);
	}

	/**
	 * Negates each 64-bit lane in the V128 vector.
	 * @return A new V128 with negated values in each of the 2 lanes
	 */
	public V128 neg64() {
		return unary64(n -> -n);
	}

	/**
	 * Adds two V128 vectors across 16 lanes of 8-bit values.
	 * @param b The second V128 vector to add
	 * @return A new V128 with the sum of corresponding lanes
	 */
	public V128 add8(V128 b) {
		return binary8(b, (n0, n1) -> (byte)(n0 + n1));
	}

	/**
	 * Subtracts the second V128 vector from the first across 16 lanes of 8-bit values.
	 * @param b The V128 vector to subtract
	 * @return A new V128 with the difference of corresponding lanes
	 */
	public V128 sub8(V128 b) {
		return binary8(b, (n0, n1) -> (byte)(n0 - n1));
	}

	/**
	 * Adds two V128 vectors across 8 lanes of 16-bit values.
	 * @param b The second V128 vector to add
	 * @return A new V128 with the sum of corresponding lanes
	 */
	public V128 add16(V128 b) {
		return binary16(b, (n0, n1) -> (short)(n0 + n1));
	}

	/**
	 * Subtracts the second V128 vector from the first across 8 lanes of 16-bit values.
	 * @param b The V128 vector to subtract
	 * @return A new V128 with the difference of corresponding lanes
	 */
	public V128 sub16(V128 b) {
		return binary16(b, (n0, n1) -> (short)(n0 - n1));
	}

	/**
	 * Adds two V128 vectors across 4 lanes of 32-bit values.
	 * @param b The second V128 vector to add
	 * @return A new V128 with the sum of corresponding lanes
	 */
	public V128 add32(V128 b) {
		return binary32(b, Integer::sum);
	}

	/**
	 * Subtracts the second V128 vector from the first across 4 lanes of 32-bit values.
	 * @param b The V128 vector to subtract
	 * @return A new V128 with the difference of corresponding lanes
	 */
	public V128 sub32(V128 b) {
		return binary32(b, (n0, n1) -> n0 - n1);
	}

	/**
	 * Adds two V128 vectors across 2 lanes of 64-bit values.
	 * @param b The second V128 vector to add
	 * @return A new V128 with the sum of corresponding lanes
	 */
	public V128 add64(V128 b) {
		return binary64(b, Long::sum);
	}

	/**
	 * Subtracts the second V128 vector from the first across 2 lanes of 64-bit values.
	 * @param b The V128 vector to subtract
	 * @return A new V128 with the difference of corresponding lanes
	 */
	public V128 sub64(V128 b) {
		return binary64(b, (n0, n1) -> n0 - n1);
	}

	/**
	 * Shifts each 8-bit lane left by the specified amount.
	 * @param amount The number of bits to shift (0-7)
	 * @return A new V128 with each lane shifted left
	 */
	public V128 shl8(int amount) {
		return unary8(n -> (byte)(n << (amount & 0x7)));
	}

	/**
	 * Shifts each 8-bit lane right by the specified amount (unsigned).
	 * @param amount The number of bits to shift (0-7)
	 * @return A new V128 with each lane shifted right (zero-filled)
	 */
	public V128 shrU8(int amount) {
		return unary8(n -> (byte)(Byte.toUnsignedInt(n) >>> (amount & 0x7)));
	}

	/**
	 * Shifts each 8-bit lane right by the specified amount (signed).
	 * @param amount The number of bits to shift (0-7)
	 * @return A new V128 with each lane shifted right (sign-extended)
	 */
	public V128 shrS8(int amount) {
		return unary8(n -> (byte)(n >> (amount & 0x7)));
	}

	/**
	 * Shifts each 16-bit lane left by the specified amount.
	 * @param amount The number of bits to shift (0-15)
	 * @return A new V128 with each lane shifted left
	 */
	public V128 shl16(int amount) {
		return unary16(n -> (short)(n << (amount & 0xF)));
	}

	/**
	 * Shifts each 16-bit lane right by the specified amount (unsigned).
	 * @param amount The number of bits to shift (0-15)
	 * @return A new V128 with each lane shifted right (zero-filled)
	 */
	public V128 shrU16(int amount) {
		return unary16(n -> (short)(Short.toUnsignedInt(n) >>> (amount & 0xF)));
	}

	/**
	 * Shifts each 16-bit lane right by the specified amount (signed).
	 * @param amount The number of bits to shift (0-15)
	 * @return A new V128 with each lane shifted right (sign-extended)
	 */
	public V128 shrS16(int amount) {
		return unary16(n -> (short)(n >> (amount & 0xF)));
	}

	/**
	 * Shifts each 32-bit lane left by the specified amount.
	 * @param amount The number of bits to shift (0-31)
	 * @return A new V128 with each lane shifted left
	 */
	public V128 shl32(int amount) {
		return unary32(n -> n << (amount & 0x1F));
	}

	/**
	 * Shifts each 32-bit lane right by the specified amount (unsigned).
	 * @param amount The number of bits to shift (0-31)
	 * @return A new V128 with each lane shifted right (zero-filled)
	 */
	public V128 shrU32(int amount) {
		return unary32(n -> n >>> (amount & 0x1F));
	}

	/**
	 * Shifts each 32-bit lane right by the specified amount (signed).
	 * @param amount The number of bits to shift (0-31)
	 * @return A new V128 with each lane shifted right (sign-extended)
	 */
	public V128 shrS32(int amount) {
		return unary32(n -> n >> (amount & 0x1F));
	}

	/**
	 * Shifts each 64-bit lane left by the specified amount.
	 * @param amount The number of bits to shift (0-63)
	 * @return A new V128 with each lane shifted left
	 */
	public V128 shl64(int amount) {
		return unary64(n -> n << (amount & 0x3F));
	}

	/**
	 * Shifts each 64-bit lane right by the specified amount (unsigned).
	 * @param amount The number of bits to shift (0-63)
	 * @return A new V128 with each lane shifted right (zero-filled)
	 */
	public V128 shrU64(int amount) {
		return unary64(n -> n >>> (amount & 0x3F));
	}

	/**
	 * Shifts each 64-bit lane right by the specified amount (signed).
	 * @param amount The number of bits to shift (0-63)
	 * @return A new V128 with each lane shifted right (sign-extended)
	 */
	public V128 shrS64(int amount) {
		return unary64(n -> n >> (amount & 0x3F));
	}


	/**
	 * Computes the rounded average of two V128 vectors across 16 lanes of 8-bit unsigned values.
	 * The average is calculated as (a + b + 1) / 2 for each lane.
	 * @param b The second V128 vector to average with
	 * @return A new V128 with the rounded average of corresponding lanes
	 */
	public V128 avgrU8(V128 b) {
		return binary8(b, (n0, n1) -> {
			int sum = Byte.toUnsignedInt(n0) + Byte.toUnsignedInt(n1) + 1;
			return (byte)(sum >>> 1);
		});
	}

	/**
	 * Computes the rounded average of two V128 vectors across 8 lanes of 16-bit unsigned values.
	 * The average is calculated as (a + b + 1) / 2 for each lane.
	 * @param b The second V128 vector to average with
	 * @return A new V128 with the rounded average of corresponding lanes
	 */
	public V128 avgrU16(V128 b) {
		return binary16(b, (n0, n1) -> {
			int sum = Short.toUnsignedInt(n0) + Short.toUnsignedInt(n1) + 1;
			return (short)(sum >>> 1);
		});
	}

	/**
	 * Computes the signed minimum of two V128 vectors across 16 lanes of 8-bit values.
	 * @param b The second V128 vector to compare
	 * @return A new V128 with the minimum of corresponding lanes (signed)
	 */
	public V128 minS8(V128 b) {
		return binary8(b, (n0, n1) -> (byte)Math.min(n0, n1));
	}

	/**
	 * Computes the unsigned minimum of two V128 vectors across 16 lanes of 8-bit values.
	 * @param b The second V128 vector to compare
	 * @return A new V128 with the minimum of corresponding lanes (unsigned)
	 */
	public V128 minU8(V128 b) {
		return binary8(b, (n0, n1) -> (byte)Math.min(Byte.toUnsignedInt(n0), Byte.toUnsignedInt(n1)));
	}

	/**
	 * Computes the signed maximum of two V128 vectors across 16 lanes of 8-bit values.
	 * @param b The second V128 vector to compare
	 * @return A new V128 with the maximum of corresponding lanes (signed)
	 */
	public V128 maxS8(V128 b) {
		return binary8(b, (n0, n1) -> (byte)Math.max(n0, n1));
	}

	/**
	 * Computes the unsigned maximum of two V128 vectors across 16 lanes of 8-bit values.
	 * @param b The second V128 vector to compare
	 * @return A new V128 with the maximum of corresponding lanes (unsigned)
	 */
	public V128 maxU8(V128 b) {
		return binary8(b, (n0, n1) -> (byte)Math.max(Byte.toUnsignedInt(n0), Byte.toUnsignedInt(n1)));
	}

	/**
	 * Computes the signed minimum of two V128 vectors across 8 lanes of 16-bit values.
	 * @param b The second V128 vector to compare
	 * @return A new V128 with the minimum of corresponding lanes (signed)
	 */
	public V128 minS16(V128 b) {
		return binary16(b, (n0, n1) -> (short)Math.min(n0, n1));
	}

	/**
	 * Computes the unsigned minimum of two V128 vectors across 8 lanes of 16-bit values.
	 * @param b The second V128 vector to compare
	 * @return A new V128 with the minimum of corresponding lanes (unsigned)
	 */
	public V128 minU16(V128 b) {
		return binary16(b, (n0, n1) -> (short)Math.min(Short.toUnsignedInt(n0), Short.toUnsignedInt(n1)));
	}

	/**
	 * Computes the signed maximum of two V128 vectors across 8 lanes of 16-bit values.
	 * @param b The second V128 vector to compare
	 * @return A new V128 with the maximum of corresponding lanes (signed)
	 */
	public V128 maxS16(V128 b) {
		return binary16(b, (n0, n1) -> (short)Math.max(n0, n1));
	}

	/**
	 * Computes the unsigned maximum of two V128 vectors across 8 lanes of 16-bit values.
	 * @param b The second V128 vector to compare
	 * @return A new V128 with the maximum of corresponding lanes (unsigned)
	 */
	public V128 maxU16(V128 b) {
		return binary16(b, (n0, n1) -> (short)Math.max(Short.toUnsignedInt(n0), Short.toUnsignedInt(n1)));
	}

	/**
	 * Computes the signed minimum of two V128 vectors across 4 lanes of 32-bit values.
	 * @param b The second V128 vector to compare
	 * @return A new V128 with the minimum of corresponding lanes (signed)
	 */
	public V128 minS32(V128 b) {
		return binary32(b, Math::min);
	}

	/**
	 * Computes the unsigned minimum of two V128 vectors across 4 lanes of 32-bit values.
	 * @param b The second V128 vector to compare
	 * @return A new V128 with the minimum of corresponding lanes (unsigned)
	 */
	public V128 minU32(V128 b) {
		return binary32(b, Util::minU32);
	}

	/**
	 * Computes the signed maximum of two V128 vectors across 4 lanes of 32-bit values.
	 * @param b The second V128 vector to compare
	 * @return A new V128 with the maximum of corresponding lanes (signed)
	 */
	public V128 maxS32(V128 b) {
		return binary32(b, Math::max);
	}

	/**
	 * Computes the unsigned maximum of two V128 vectors across 4 lanes of 32-bit values.
	 * @param b The second V128 vector to compare
	 * @return A new V128 with the maximum of corresponding lanes (unsigned)
	 */
	public V128 maxU32(V128 b) {
		return binary32(b, Util::maxU32);
	}


	/**
	 * Performs saturating signed addition of two V128 vectors across 16 lanes of 8-bit values.
	 * @param b The second V128 vector to add
	 * @return A new V128 with saturated sums of corresponding lanes (signed)
	 */
	public V128 addSatS8(V128 b) {
		return binary8(b, Util::addSatS8);
	}

	/**
	 * Performs saturating unsigned addition of two V128 vectors across 16 lanes of 8-bit values.
	 * @param b The second V128 vector to add
	 * @return A new V128 with saturated sums of corresponding lanes (unsigned)
	 */
	public V128 addSatU8(V128 b) {
		return binary8(b, Util::addSatU8);
	}

	/**
	 * Performs saturating signed subtraction of two V128 vectors across 16 lanes of 8-bit values.
	 * @param b The V128 vector to subtract
	 * @return A new V128 with saturated differences of corresponding lanes (signed)
	 */
	public V128 subSatS8(V128 b) {
		return binary8(b, Util::subSatS8);
	}

	/**
	 * Performs saturating unsigned subtraction of two V128 vectors across 16 lanes of 8-bit values.
	 * @param b The V128 vector to subtract
	 * @return A new V128 with saturated differences of corresponding lanes (unsigned)
	 */
	public V128 subSatU8(V128 b) {
		return binary8(b, Util::subSatU8);
	}

	/**
	 * Performs saturating signed addition of two V128 vectors across 8 lanes of 16-bit values.
	 * @param b The second V128 vector to add
	 * @return A new V128 with saturated sums of corresponding lanes (signed)
	 */
	public V128 addSatS16(V128 b) {
		return binary16(b, Util::addSatS16);
	}

	/**
	 * Performs saturating unsigned addition of two V128 vectors across 8 lanes of 16-bit values.
	 * @param b The second V128 vector to add
	 * @return A new V128 with saturated sums of corresponding lanes (unsigned)
	 */
	public V128 addSatU16(V128 b) {
		return binary16(b, Util::addSatU16);
	}

	/**
	 * Performs saturating signed subtraction of two V128 vectors across 8 lanes of 16-bit values.
	 * @param b The V128 vector to subtract
	 * @return A new V128 with saturated differences of corresponding lanes (signed)
	 */
	public V128 subSatS16(V128 b) {
		return binary16(b, Util::subSatS16);
	}

	/**
	 * Performs saturating unsigned subtraction of two V128 vectors across 8 lanes of 16-bit values.
	 * @param b The V128 vector to subtract
	 * @return A new V128 with saturated differences of corresponding lanes (unsigned)
	 */
	public V128 subSatU16(V128 b) {
		return binary16(b, Util::subSatU16);
	}
	
	/**
	 * Multiplies two V128 vectors across 8 lanes of 16-bit values.
	 * Results wrap on overflow.
	 * @param b The second V128 vector to multiply with
	 * @return A new V128 with the products of corresponding lanes
	 */
	public V128 mul16(V128 b) {
		return binary16(b, (n0, n1) -> (short)(n0 * n1));
	}

	/**
	 * Multiplies two V128 vectors across 4 lanes of 32-bit values.
	 * Results wrap on overflow.
	 * @param b The second V128 vector to multiply with
	 * @return A new V128 with the products of corresponding lanes
	 */
	public V128 mul32(V128 b) {
		return binary32(b, (n0, n1) -> n0 * n1);
	}

	/**
	 * Multiplies two V128 vectors across 2 lanes of 64-bit values.
	 * Results wrap on overflow.
	 * @param b The second V128 vector to multiply with
	 * @return A new V128 with the products of corresponding lanes
	 */
	public V128 mul64(V128 b) {
		return binary64(b, (n0, n1) -> n0 * n1);
	}

	/**
	 * Multiplies the lower 8 lanes of two V128 vectors as signed 8-bit values,
	 * producing 16-bit results.
	 * @param b The second V128 vector to multiply with
	 * @return A new V128 with 8 lanes containing the products of the lower 8 lanes
	 */
	public V128 extmulLowS8(V128 b) {
		return V128.build16(i -> (short)(this.extractLane8(i) * b.extractLane8(i)));
	}

	/**
	 * Multiplies the upper 8 lanes of two V128 vectors as signed 8-bit values,
	 * producing 16-bit results.
	 * @param b The second V128 vector to multiply with
	 * @return A new V128 with 8 lanes containing the products of the upper 8 lanes
	 */
	public V128 extmulHighS8(V128 b) {
		return V128.build16(i -> (short)(this.extractLane8(i + 8) * b.extractLane8(i + 8)));
	}

	/**
	 * Multiplies the lower 8 lanes of two V128 vectors as unsigned 8-bit values,
	 * producing 16-bit results.
	 * @param b The second V128 vector to multiply with
	 * @return A new V128 with 8 lanes containing the products of the lower 8 lanes
	 */
	public V128 extmulLowU8(V128 b) {
		return V128.build16(i -> (short)(Byte.toUnsignedInt(this.extractLane8(i)) * Byte.toUnsignedInt(b.extractLane8(i))));
	}

	/**
	 * Multiplies the upper 8 lanes of two V128 vectors as unsigned 8-bit values,
	 * producing 16-bit results.
	 * @param b The second V128 vector to multiply with
	 * @return A new V128 with 8 lanes containing the products of the upper 8 lanes
	 */
	public V128 extmulHighU8(V128 b) {
		return V128.build16(i -> (short)(Byte.toUnsignedInt(this.extractLane8(i + 8)) * Byte.toUnsignedInt(b.extractLane8(i + 8))));
	}

	// Extended addition operations

	/**
	 * Sums adjacent pairs of signed 8-bit values from a V128 vector,
	 * producing 16-bit results.
	 * @return A new V128 with 8 lanes containing the sums of adjacent pairs
	 */
	public V128 extaddPairwiseS8() {
		return V128.build16(i -> (short)(extractLane8(i) + extractLane8(i + 8)));
	}

	/**
	 * Sums adjacent pairs of unsigned 8-bit values from a V128 vector,
	 * producing 16-bit results.
	 * @return A new V128 with 8 lanes containing the sums of adjacent pairs
	 */
	public V128 extaddPairwiseU8() {
		return V128.build16(i -> (short)(Byte.toUnsignedInt(extractLane8(i)) + Byte.toUnsignedInt(extractLane8(i + 8))));
	}

	@Override
	public String toString() {
		var sb = new StringBuilder();
		sb.append("V128(");
		for(int i = 0; i < 16; ++i) {
			sb.append(String.format("%02x", extractLane8(i)));
			if(i < 15) {
				sb.append(", ");
			}
		}
		sb.append(")");
		return sb.toString();
	}
}
