package dev.argon.jvmwasm.format.data;

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
	 * @param a The first set of values.
	 * @param b The second set of values.
	 * @return The result.
	 */
	public V128 shuffle8(V128 a, V128 b) {
		return build8(i -> {
			int index = extractLane8(i);
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
	public V128 replaceLane8(int index, byte value) {
		return build8(i -> i == index ? value : extractLane8(i));
	}


	/**
	 * Replace a 16-bit lane.
	 * @param index The index.
	 * @param value The value.
	 * @return The result.
	 */
	public V128 replaceLane16(int index, short value) {
		return build16(i -> i == index ? value : extractLane16(i));
	}

	/**
	 * Replace a 32-bit lane.
	 * @param index The index.
	 * @param value The value.
	 * @return The result.
	 */
	public V128 replaceLane32(int index, int value) {
		return build32(i -> i == index ? value : extractLane32(i));
	}

	/**
	 * Replace a 64-bit lane.
	 * @param index The index.
	 * @param value The value.
	 * @return The result.
	 */
	public V128 replaceLane64(int index, long value) {
		return build64(i -> i == index ? value : extractLane64(i));
	}

	/**
	 * Replace a 32-bit float lane.
	 * @param index The index.
	 * @param value The value.
	 * @return The result.
	 */
	public V128 replaceLaneF32(int index, float value) {
		return buildF32(i -> i == index ? value : extractLaneF32(i));
	}

	/**
	 * Replace a 64-bit float lane.
	 * @param index The index.
	 * @param value The value.
	 * @return The result.
	 */
	public V128 replaceLaneF64(int index, double value) {
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
