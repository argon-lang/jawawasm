package jvmwasm.engine;

import java.math.BigDecimal;

public final class Util {
	private Util() {}


	public static final int PAGE_SIZE = 64 * 1024;


	public static int divideS32(int a, int b) {
		if(a == Integer.MIN_VALUE && b == -1) {
			throw new ArithmeticException();
		}
		return a / b;
	}

	public static long divideS64(long a, long b) {
		if(a == Long.MIN_VALUE && b == -1) {
			throw new ArithmeticException();
		}
		return a / b;
	}


	public static boolean sumInRange(int a, int b, int size) {
		return a >= 0 && b >= 0 && (a + b) >= 0 && (a + b) <= size;
	}

	public static byte narrowU16I8(short a) {
		if(a < 0) {
			return 0;
		}
		else if(a > 255) {
			return -1;
		}
		else {
			return (byte)a;
		}
	}

	public static int truncF64ToS32(double a) {
		if(!Double.isFinite(a)) {
			throw new ArithmeticException();
		}

		var value = new BigDecimal(a);
		if(value.compareTo(new BigDecimal((long)Integer.MIN_VALUE - 1)) <= 0 || value.compareTo(new BigDecimal((long)Integer.MAX_VALUE + 1)) >= 0) {
			throw new ArithmeticException();
		}

		return value.intValue();
	}

	public static long truncF64ToS64(double a) {
		if(!Double.isFinite(a)) {
			throw new ArithmeticException();
		}

		var value = new BigDecimal(a);
		if(value.compareTo(new BigDecimal(Long.MIN_VALUE).subtract(BigDecimal.ONE)) <= 0 || value.compareTo(new BigDecimal(Long.MAX_VALUE).add(BigDecimal.ONE)) >= 0) {
			throw new ArithmeticException();
		}

		return value.longValue();
	}

	public static int truncF64ToU32(double a) {
		if(!Double.isFinite(a) || a <= -1.0) {
			throw new ArithmeticException();
		}

		var value = new BigDecimal(a);
		if(value.compareTo(new BigDecimal(4294967296L)) >= 0) {
			throw new ArithmeticException();
		}

		return value.intValue();
	}

	public static long truncF64ToU64(double a) {
		if(!Double.isFinite(a) || a <= -1.0) {
			throw new ArithmeticException();
		}

		var value = new BigDecimal(a);
		if(value.compareTo(new BigDecimal("18446744073709551616")) >= 0) {
			throw new ArithmeticException();
		}

		return value.longValue();
	}

	public static float u64ToF32(long a) {
		return new BigDecimal(Long.toUnsignedString(a)).floatValue();
	}

	public static double u64ToF64(long a) {
		return new BigDecimal(Long.toUnsignedString(a)).doubleValue();
	}

	public static byte narrowS16I8(short a) {
		if(a < Byte.MIN_VALUE) {
			return Byte.MIN_VALUE;
		}
		else if(a > Byte.MAX_VALUE) {
			return Byte.MAX_VALUE;
		}
		else {
			return (byte)a;
		}
	}

	public static short narrowU32I16(int a) {
		if(a < 0) {
			return 0;
		}
		else if(a > 65535) {
			return -1;
		}
		else {
			return (short)a;
		}
	}

	public static short narrowS32I16(int a) {
		if(a < Short.MIN_VALUE) {
			return Short.MIN_VALUE;
		}
		else if(a > Short.MAX_VALUE) {
			return Short.MAX_VALUE;
		}
		else {
			return (short)a;
		}
	}

	public static byte addSatU8(byte a, byte b) {
		return narrowU16I8((short)(Byte.toUnsignedInt(a) + Byte.toUnsignedInt(b)));
	}

	public static byte addSatS8(byte a, byte b) {
		return narrowS16I8((short)(a + b));
	}

	public static byte subSatU8(byte a, byte b) {
		int result = Byte.toUnsignedInt(a) - Byte.toUnsignedInt(b);
		if(result < 0) {
			return 0;
		}
		else {
			return (byte)result;
		}
	}

	public static byte subSatS8(byte a, byte b) {
		return narrowS16I8((short)(a - b));
	}

	public static short addSatU16(short a, short b) {
		return narrowU32I16(Short.toUnsignedInt(a) + Short.toUnsignedInt(b));
	}

	public static short addSatS16(short a, short b) {
		return narrowS32I16(a + b);
	}

	public static short subSatU16(short a, short b) {
		int result = Short.toUnsignedInt(a) - Short.toUnsignedInt(b);
		if(result < 0) {
			return 0;
		}
		else {
			return (short)result;
		}
	}

	public static short subSatS16(short a, short b) {
		return narrowS32I16(a - b);
	}

	public static int minU32(int a, int b) {
		if(Integer.compareUnsigned(a, b) < 0) {
			return a;
		}
		else {
			return b;
		}
	}

	public static int maxU32(int a, int b) {
		if(Integer.compareUnsigned(a, b) > 0) {
			return a;
		}
		else {
			return b;
		}
	}

	public static float ceilF32(float a) {
		if(Float.isNaN(a)) {
			return Float.NaN;
		}
		else {
			return (float)Math.ceil(a);
		}
	}

	public static double ceilF64(double a) {
		if(Double.isNaN(a)) {
			return Double.NaN;
		}
		else {
			return Math.ceil(a);
		}
	}

	public static float floorF32(float a) {
		if(Float.isNaN(a)) {
			return Float.NaN;
		}
		else {
			return (float)Math.floor(a);
		}
	}

	public static double floorF64(double a) {
		if(Double.isNaN(a)) {
			return Double.NaN;
		}
		else {
			return Math.floor(a);
		}
	}

	public static float truncF32(float a) {
		if(Float.isNaN(a)) {
			return Float.NaN;
		}
		else if(Float.isInfinite(a) || a == 0) {
			return a;
		}
		else if(a < 0 && a > -1) {
			return -0.0f;
		}
		else if(a < 0) {
			return (float)Math.ceil(a);
		}
		else {
			return (float)Math.floor(a);
		}
	}

	public static double truncF64(double a) {
		if(Double.isNaN(a)) {
			return Double.NaN;
		}
		else if(Double.isInfinite(a) || a == 0) {
			return a;
		}
		else if(a < 0 && a > -1) {
			return -0.0;
		}
		else if(a < 0) {
			return Math.ceil(a);
		}
		else {
			return Math.floor(a);
		}
	}

	public static float nearestF32(float a) {
		if(Float.isNaN(a)) {
			return Float.NaN;
		}
		else if(Float.isInfinite(a) || a == 0) {
			return a;
		}
		else if(a < 0 && a >= -0.5f) {
			return -0.0f;
		}
		else {
			return (float)Math.rint(a);
		}
	}

	public static double nearestF64(double a) {
		if(Double.isNaN(a)) {
			return Double.NaN;
		}
		else if(Double.isInfinite(a) || a == 0) {
			return a;
		}
		else if(a < 0 && a >= -0.5) {
			return -0.0;
		}
		else {
			return Math.rint(a);
		}
	}

	public static int truncSatF32U32(float a) {
		if(Float.isNaN(a) || a < 0) {
			return 0;
		}
		else if(a == Float.POSITIVE_INFINITY) {
			return -1;
		}
		else {
			long value = (long)a;
			if(value <= Integer.toUnsignedLong(-1)) {
				return (int)value;
			}
			else {
				return -1;
			}
		}
	}

	public static int truncSatF64U32(double a) {
		if(Double.isNaN(a) || a < 0) {
			return 0;
		}
		else if(a == Double.POSITIVE_INFINITY) {
			return -1;
		}
		else {
			long value = (long)a;
			if(value <= Integer.toUnsignedLong(-1)) {
				return (int)value;
			}
			else {
				return -1;
			}
		}
	}

	public static long truncSatF32U64(float a) {
		if(Float.isNaN(a) || a < 0) {
			return 0L;
		}
		else if(a == Float.POSITIVE_INFINITY) {
			return -1L;
		}
		else {
			BigDecimal value = new BigDecimal(a);
			if(value.compareTo(new BigDecimal("18446744073709551615")) <= 0) {
				return value.longValue();
			}
			else {
				return -1L;
			}
		}
	}

	public static long truncSatF64U64(double a) {
		if(Double.isNaN(a) || a < 0) {
			return 0L;
		}
		else if(a == Double.POSITIVE_INFINITY) {
			return -1L;
		}
		else {
			BigDecimal value = new BigDecimal(a);
			if(value.compareTo(new BigDecimal("18446744073709551615")) <= 0) {
				return value.longValue();
			}
			else {
				return -1L;
			}
		}
	}

	public static float minF32(float a, float b) {
		if(Float.isNaN(a) || Float.isNaN(b)) {
			return Float.NaN;
		}
		else if(b == 0.0f && a == 0.0f) {
			if(Math.copySign(1.0f, a) < 0) {
				return a;
			}
			else {
				return b;
			}
		}
		else {
			return Math.min(a, b);
		}
	}

	public static double minF64(double a, double b) {
		if(Double.isNaN(a) || Double.isNaN(b)) {
			return Double.NaN;
		}
		else if(b == 0.0 && a == 0.0) {
			if(Math.copySign(1.0, a) < 0) {
				return a;
			}
			else {
				return b;
			}
		}
		else {
			return Math.min(a, b);
		}
	}

	public static float maxF32(float a, float b) {
		if(Float.isNaN(a) || Float.isNaN(b)) {
			return Float.NaN;
		}
		else if(b == 0.0f && a == 0.0f) {
			if(Math.copySign(1.0f, a) > 0) {
				return a;
			}
			else {
				return b;
			}
		}
		else {
			return Math.max(a, b);
		}
	}

	public static double maxF64(double a, double b) {
		if(Double.isNaN(a) || Double.isNaN(b)) {
			return Double.NaN;
		}
		else if(b == 0.0 && a == 0.0) {
			if(Math.copySign(1.0, a) > 0) {
				return a;
			}
			else {
				return b;
			}
		}
		else {
			return Math.max(a, b);
		}
	}



}
