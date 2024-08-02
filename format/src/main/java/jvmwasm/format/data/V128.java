package jvmwasm.format.data;

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

	public float extractLaneF32(int i) {
		return Float.intBitsToFloat(extractLane32(i));
	}

	public double extractLaneF64(int i) {
		return Double.longBitsToDouble(extractLane64(i));
	}

	public static interface Build8Function {
		byte apply(int index);
	}

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

	public static interface Build16Function {
		short apply(int index);
	}

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

	public static interface Build32Function {
		int apply(int index);
	}

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

	public static interface Build64Function {
		long apply(int index);
	}

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

	public static interface BuildF32Function {
		float apply(int index);
	}

	public static V128 buildF32(BuildF32Function f) {
		return build32(i -> Float.floatToRawIntBits(f.apply(i)));
	}

	public static interface BuildF64Function {
		double apply(int index);
	}

	public static V128 buildF64(BuildF64Function f) {
		return build64(i -> Double.doubleToRawLongBits(f.apply(i)));
	}


	public static interface Unary8Function {
		byte apply(byte a);
	}
	
	public V128 unary8(Unary8Function f) {
		return build8(i -> f.apply(extractLane8(i)));
	}

	public static interface Unary16Function {
		short apply(short a);
	}

	public V128 unary16(Unary16Function f) {
		return build16(i -> f.apply(extractLane16(i)));
	}

	public static interface Unary32Function {
		int apply(int a);
	}

	public V128 unary32(Unary32Function f) {
		return build32(i -> f.apply(extractLane32(i)));
	}

	public static interface Unary64Function {
		long apply(long a);
	}

	public V128 unary64(Unary64Function f) {
		return build64(i -> f.apply(extractLane64(i)));
	}

	public static interface UnaryF32Function {
		float apply(float a);
	}

	public V128 unaryF32(UnaryF32Function f) {
		return buildF32(i -> f.apply(extractLaneF32(i)));
	}

	public static interface UnaryF64Function {
		double apply(double a);
	}

	public V128 unaryF64(UnaryF64Function f) {
		return buildF64(i -> f.apply(extractLaneF64(i)));
	}

	public static interface Binary8Function {
		byte apply(byte a, byte b);
	}

	public V128 binary8(V128 other, Binary8Function f) {
		return build8(i -> f.apply(extractLane8(i), other.extractLane8(i)));
	}

	public static interface Binary16Function {
		short apply(short a, short b);
	}

	public V128 binary16(V128 other, Binary16Function f) {
		return build16(i -> f.apply(extractLane16(i), other.extractLane16(i)));
	}

	public static interface Binary32Function {
		int apply(int a, int b);
	}

	public V128 binary32(V128 other, Binary32Function f) {
		return build32(i -> f.apply(extractLane32(i), other.extractLane32(i)));
	}

	public static interface Binary64Function {
		long apply(long a, long b);
	}

	public V128 binary64(V128 other, Binary64Function f) {
		return build64(i -> f.apply(extractLane64(i), other.extractLane64(i)));
	}

	public static interface BinaryF32Function {
		float apply(float a, float b);
	}

	public V128 binaryF32(V128 other, BinaryF32Function f) {
		return buildF32(i -> f.apply(extractLaneF32(i), other.extractLaneF32(i)));
	}

	public static interface BinaryF64Function {
		double apply(double a, double b);
	}

	public V128 binaryF64(V128 other, BinaryF64Function f) {
		return buildF64(i -> f.apply(extractLaneF64(i), other.extractLaneF64(i)));
	}
	
	public static interface Ternary8Function {
		byte apply(byte a, byte b, byte c);
	}

	public V128 ternary8(V128 second, V128 third, Ternary8Function f) {
		return build8(i -> f.apply(extractLane8(i), second.extractLane8(i), third.extractLane8(i)));
	}



	public boolean anyTrue() {
		for(int i = 0; i < 16; ++i) {
			if(extractLane8(i) != 0) {
				return true;
			}
		}

		return false;
	}

	
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

	public V128 shuffle8(V128 indexes, V128 a, V128 b) {
		return build8(i -> {
			int index = indexes.extractLane8(i);
			if(index < 16) {
				return a.extractLane8(index);
			}
			else {
				return b.extractLane8(index - 16);
			}
		});
	}

	public static V128 splat8(byte value) {
		return build8(i -> value);
	}

	public static V128 splat16(short value) {
		return build16(i -> value);
	}

	public static V128 splat32(int value) {
		return build32(i -> value);
	}

	public static V128 splat64(long value) {
		return build64(i -> value);
	}

	public static V128 splatF32(float value) {
		return buildF32(i -> value);
	}

	public static V128 splatF64(double value) {
		return buildF64(i -> value);
	}

	public V128 replaceLane8(int index, byte value) {
		return build8(i -> i == index ? value : extractLane8(i));
	}

	public V128 replaceLane16(int index, short value) {
		return build16(i -> i == index ? value : extractLane16(i));
	}

	public V128 replaceLane32(int index, int value) {
		return build32(i -> i == index ? value : extractLane32(i));
	}

	public V128 replaceLane64(int index, long value) {
		return build64(i -> i == index ? value : extractLane64(i));
	}

	public V128 replaceLaneF32(int index, float value) {
		return buildF32(i -> i == index ? value : extractLaneF32(i));
	}

	public V128 replaceLaneF64(int index, double value) {
		return buildF64(i -> i == index ? value : extractLaneF64(i));
	}

	public boolean allTrue8() {
		for(int i = 0; i < 16; ++i) {
			if(extractLane8(i) == 0) {
				return false;
			}
		}

		return true;
	}

	public boolean allTrue16() {
		for(int i = 0; i < 8; ++i) {
			if(extractLane16(i) == 0) {
				return false;
			}
		}

		return true;
	}

	public boolean allTrue32() {
		for(int i = 0; i < 4; ++i) {
			if(extractLane32(i) == 0) {
				return false;
			}
		}

		return true;
	}

	public boolean allTrue64() {
		for(int i = 0; i < 2; ++i) {
			if(extractLane64(i) == 0) {
				return false;
			}
		}

		return true;
	}

	public int bitmask8() {
		int result = 0;
		for(int i = 0; i < 16; ++i) {
			if(extractLane8(i) < 0) {
				result |= 1 << i;
			}
		}
		return result;
	}

	public int bitmask16() {
		int result = 0;
		for(int i = 0; i < 8; ++i) {
			if(extractLane16(i) < 0) {
				result |= 1 << i;
			}
		}
		return result;
	}

	public int bitmask32() {
		int result = 0;
		for(int i = 0; i < 4; ++i) {
			if(extractLane32(i) < 0) {
				result |= 1 << i;
			}
		}
		return result;
	}

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
