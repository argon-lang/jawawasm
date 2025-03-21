package dev.argon.jawawasm.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Utility methods called by WebAssembly
 */
public final class Util {
	private Util() {}

	/**
	 * WebAssembly memory page size in bytes
	 */
	public static final int PAGE_SIZE = 64 * 1024;

	/**
	 * Divide two signed integers
	 * @param a The numerator
	 * @param b The denominator
	 * @throws ArithmeticException if `b` is `0` or `a` is `Integer.MIN_VALUE` and `b` is `-1`
	 * @return a / b
	 */
	public static int divideS32(int a, int b) {
		if(a == Integer.MIN_VALUE && b == -1) {
			throw new ArithmeticException();
		}
		return a / b;
	}

	/**
	 * Divide two signed longs
	 * @param a The numerator
	 * @param b The denominator
	 * @throws ArithmeticException if `b` is `0` or `a` is `Log.MIN_VALUE` and `b` is `-1`
	 * @return a / b
	 */
	public static long divideS64(long a, long b) {
		if(a == Long.MIN_VALUE && b == -1) {
			throw new ArithmeticException();
		}
		return a / b;
	}

	/**
	 * Checks whether a sum is within the bounds of a size
	 * @param a The first value
	 * @param b The second value
	 * @param size The size of the range
	 * @return true if `a + b` is within the range [0, size), excluding overflow and negative inputs.
	 */
	public static boolean sumInRange(long a, long b, long size) {
		return a >= 0 && b >= 0 && (a + b) >= 0 && (a + b) <= size;
	}

	/**
	 * Narrow an u16 to an i8.
	 * @param a The u16
	 * @return The value of `a` clamped to the range of i8.
	 */
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

	/**
	 * Truncate F64 to S32
	 * @param a The F64
	 * @return The value as a signed integer
	 * @throws ArithmeticException if the value cannot be represented as a signed integer.
	 */
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

	/**
	 * Truncate F64 to S64
	 * @param a The F64 value
	 * @return The value as a signed long integer
	 * @throws ArithmeticException if the value cannot be represented as a signed long integer.
	 */
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

	/**
	 * Truncate F64 to U32
	 * @param a The F64 value
	 * @return The value as an unsigned 32-bit integer
	 * @throws ArithmeticException if the value is negative, infinite, NaN, or exceeds U32 max range.
	 */
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

	/**
	 * Truncate F64 to U64
	 * @param a The F64 value
	 * @return The value as an unsigned 64-bit integer
	 * @throws ArithmeticException if the value is negative, infinite, NaN, or exceeds U32 max range.
	 */
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

	private static final BigInteger UNSIGNED_LONG_MASK = BigInteger.ONE.shiftLeft(Long.SIZE).subtract(BigInteger.ONE);

	/**
	 * Convert unsigned 64-bit integer to F32.
	 * @param a The unsigned 64-bit integer.
	 * @return The floating-point representation.
	 */
	public static float u64ToF32(long a) {
		return BigInteger.valueOf(a).and(UNSIGNED_LONG_MASK).floatValue();
	}
	/**
	 * Convert unsigned 64-bit integer to F64.
	 * @param a The unsigned 64-bit integer.
	 * @return The double-precision floating-point representation.
	 */
	public static double u64ToF64(long a) {
		return BigInteger.valueOf(a).and(UNSIGNED_LONG_MASK).doubleValue();
	}

	/**
	 * Narrow a signed 16-bit integer to an 8-bit integer.
	 * @param a The 16-bit integer.
	 * @return The clamped 8-bit integer value.
	 */
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

	/**
	 * Narrow an unsigned 32-bit integer to a signed 16-bit integer.
	 * @param a The unsigned 32-bit integer.
	 * @return The clamped signed 16-bit integer value.
	 */
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

	/**
	 * Narrow a signed 32-bit integer to a signed 16-bit integer.
	 * @param a The unsigned 32-bit integer.
	 * @return The clamped signed 16-bit integer value.
	 */
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

	/**
	 * Saturating add for u8.
	 * @param a The first value.
	 * @param b The second value.
	 * @return The clamped result.
	 */
	public static byte addSatU8(byte a, byte b) {
		return narrowU16I8((short)(Byte.toUnsignedInt(a) + Byte.toUnsignedInt(b)));
	}

	/**
	 * Saturating add for s8.
	 * @param a The first value.
	 * @param b The second value.
	 * @return The clamped result.
	 */
	public static byte addSatS8(byte a, byte b) {
		return narrowS16I8((short)(a + b));
	}

	/**
	 * Subtracts two unsigned 8-bit bytes with saturation.
	 * If the result would be negative, returns 0 instead.
	 *
	 * @param a the first unsigned byte value
	 * @param b the second unsigned byte value to subtract from the first
	 * @return the difference of a and b as an unsigned byte, or 0 if the result would be negative
	 */
	public static byte subSatU8(byte a, byte b) {
		int result = Byte.toUnsignedInt(a) - Byte.toUnsignedInt(b);
		if(result < 0) {
			return 0;
		}
		else {
			return (byte)result;
		}
	}

	/**
	 * Subtracts two signed 8-bit bytes with saturation.
	 * The result is clamped to the range of a signed 8-bit integer (-128 to 127).
	 *
	 * @param a the first signed byte value
	 * @param b the second signed byte value to subtract from the first
	 * @return the difference of a and b as a signed byte, saturated to the 8-bit signed range
	 */
	public static byte subSatS8(byte a, byte b) {
		return narrowS16I8((short)(a - b));
	}

	/**
	 * Adds two unsigned 16-bit shorts with saturation.
	 * If the result exceeds the maximum unsigned 16-bit value (65535), it is saturated to 65535.
	 *
	 * @param a the first unsigned short value
	 * @param b the second unsigned short value to add to the first
	 * @return the sum of a and b as an unsigned short, saturated to the 16-bit unsigned range
	 */
	public static short addSatU16(short a, short b) {
		return narrowU32I16(Short.toUnsignedInt(a) + Short.toUnsignedInt(b));
	}

	/**
	 * Adds two signed 16-bit shorts with saturation.
	 * The result is clamped to the range of a signed 16-bit integer (-32768 to 32767).
	 *
	 * @param a the first signed short value
	 * @param b the second signed short value to add to the first
	 * @return the sum of a and b as a signed short, saturated to the 16-bit signed range
	 */
	public static short addSatS16(short a, short b) {
		return narrowS32I16(a + b);
	}

	/**
	 * Subtracts two unsigned 16-bit shorts with saturation.
	 * If the result would be negative, returns 0 instead.
	 *
	 * @param a the first unsigned short value
	 * @param b the second unsigned short value to subtract from the first
	 * @return the difference of a and b as an unsigned short, or 0 if the result would be negative
	 */
	public static short subSatU16(short a, short b) {
		int result = Short.toUnsignedInt(a) - Short.toUnsignedInt(b);
		if(result < 0) {
			return 0;
		}
		else {
			return (short)result;
		}
	}

	/**
	 * Subtracts two signed 16-bit shorts with saturation.
	 * The result is clamped to the range of a signed 16-bit integer (-32768 to 32767).
	 *
	 * @param a the first signed short value
	 * @param b the second signed short value to subtract from the first
	 * @return the difference of a and b as a signed short, saturated to the 16-bit signed range
	 */
	public static short subSatS16(short a, short b) {
		return narrowS32I16(a - b);
	}

	/**
	 * Compute the minimum of two unsigned 32-bit integers.
	 * @param a The first value.
	 * @param b The second value.
	 * @return The minimum value.
	 */
	public static int minU32(int a, int b) {
		if(Integer.compareUnsigned(a, b) < 0) {
			return a;
		}
		else {
			return b;
		}
	}

	/**
	 * Compute the maximum of two unsigned 32-bit integers.
	 * @param a The first value.
	 * @param b The second value.
	 * @return The maximum value.
	 */
	public static int maxU32(int a, int b) {
		if(Integer.compareUnsigned(a, b) > 0) {
			return a;
		}
		else {
			return b;
		}
	}

	/**
	 * Returns the smallest integer greater than or equal to the specified 32-bit float.
	 *
	 * @param a the float value to ceil
	 * @return the ceiling of a as a float, or NaN if the input is NaN
	 */
	public static float ceilF32(float a) {
		if(Float.isNaN(a)) {
			return Float.NaN;
		}
		else {
			return (float)Math.ceil(a);
		}
	}

	/**
	 * Returns the smallest integer greater than or equal to the specified 64-bit double.
	 *
	 * @param a the double value to ceil
	 * @return the ceiling of a as a double, or NaN if the input is NaN
	 */
	public static double ceilF64(double a) {
		if(Double.isNaN(a)) {
			return Double.NaN;
		}
		else {
			return Math.ceil(a);
		}
	}

	/**
	 * Returns the largest integer less than or equal to the specified 32-bit float.
	 *
	 * @param a the float value to floor
	 * @return the floor of a as a float, or NaN if the input is NaN
	 */
	public static float floorF32(float a) {
		if(Float.isNaN(a)) {
			return Float.NaN;
		}
		else {
			return (float)Math.floor(a);
		}
	}

	/**
	 * Returns the largest integer less than or equal to the specified 64-bit double.
	 *
	 * @param a the double value to floor
	 * @return the floor of a as a double, or NaN if the input is NaN
	 */
	public static double floorF64(double a) {
		if(Double.isNaN(a)) {
			return Double.NaN;
		}
		else {
			return Math.floor(a);
		}
	}

	/**
	 * Truncates a 32-bit float towards zero, returning the nearest integer.
	 *
	 * @param a the float value to truncate
	 * @return the truncated value of a as a float; returns NaN if input is NaN,
	 *         the input if infinite or zero, -0.0f for values between -1 and 0
	 */
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

	/**
	 * Truncates a 64-bit double towards zero, returning the nearest integer.
	 *
	 * @param a the double value to truncate
	 * @return the truncated value of a as a double; returns NaN if input is NaN,
	 *         the input if infinite or zero, -0.0 for values between -1 and 0
	 */
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

	/**
	 * Rounds a 32-bit float to the nearest integer, with ties rounding to even.
	 *
	 * @param a the float value to round
	 * @return the nearest integer to a as a float; returns NaN if input is NaN,
	 *         the input if infinite or zero, -0.0f for values between -0.5 and 0
	 */
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

	/**
	 * Rounds a 64-bit double to the nearest integer, with ties rounding to even.
	 *
	 * @param a the double value to round
	 * @return the nearest integer to a as a double; returns NaN if input is NaN,
	 *         the input if infinite or zero, -0.0 for values between -0.5 and 0
	 */
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

	/**
	 * Truncates a 32-bit float to an unsigned 32-bit integer with saturation.
	 *
	 * @param a the float value to truncate
	 * @return the truncated value as an unsigned int; returns 0 if NaN or negative,
	 *         -1 (max unsigned int) if infinity or exceeds 2^32-1
	 */
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
	/**
	 * Truncates a 64-bit double to an unsigned 32-bit integer with saturation.
	 *
	 * @param a the double value to truncate
	 * @return the truncated value as an unsigned int; returns 0 if NaN or negative,
	 *         -1 (max unsigned int) if infinity or exceeds 2^32-1
	 */
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

	/**
	 * Truncates a 32-bit float to an unsigned 64-bit integer with saturation.
	 *
	 * @param a the float value to truncate
	 * @return the truncated value as an unsigned long; returns 0 if NaN or negative,
	 *         -1 (max unsigned long) if infinity or exceeds 2^64-1
	 */
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

	/**
	 * Truncates a 64-bit double to an unsigned 64-bit integer with saturation.
	 *
	 * @param a the double value to truncate
	 * @return the truncated value as an unsigned long; returns 0 if NaN or negative,
	 *         -1 (max unsigned long) if infinity or exceeds 2^64-1
	 */
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

	/**
	 * Returns the minimum of two 32-bit float values.
	 *
	 * @param a the first float value
	 * @param b the second float value
	 * @return the smaller of a and b; returns NaN if either input is NaN,
	 *         prefers -0.0f over +0.0f when both are zero
	 */
	public static float min(float a, float b) {
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

	/**
	 * Returns the minimum of two 64-bit double values.
	 *
	 * @param a the first double value
	 * @param b the second double value
	 * @return the smaller of a and b; returns NaN if either input is NaN,
	 *         prefers -0.0 over +0.0 when both are zero
	 */
	public static double min(double a, double b) {
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

	/**
	 * Returns the maximum of two 32-bit float values.
	 *
	 * @param a the first float value
	 * @param b the second float value
	 * @return the larger of a and b; returns NaN if either input is NaN,
	 *         prefers +0.0f over -0.0f when both are zero
	 */
	public static float max(float a, float b) {
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

	/**
	 * Returns the maximum of two 64-bit double values.
	 *
	 * @param a the first double value
	 * @param b the second double value
	 * @return the larger of a and b; returns NaN if either input is NaN,
	 *         prefers +0.0 over -0.0 when both are zero
	 */
	public static double max(double a, double b) {
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

	/**
	 * Checks if the given integer value equals zero.
	 *
	 * @param value the integer value to check
	 * @return true if the value is zero, false otherwise
	 */
	public static boolean equalsZero(int value) {
		return value == 0;
	}

	/**
	 * Checks if the given long value equals zero.
	 *
	 * @param value the long value to check
	 * @return true if the value is zero, false otherwise
	 */
	public static boolean equalsZero(long value) {
		return value == 0;
	}

	/**
	 * Compares two integers for equality.
	 *
	 * @param a first integer to compare
	 * @param b second integer to compare
	 * @return true if the integers are equal, false otherwise
	 */
	public static boolean numEquals(int a, int b) {
		return a == b;
	}

	/**
	 * Checks if two integers are not equal.
	 *
	 * @param a first integer to compare
	 * @param b second integer to compare
	 * @return true if the integers are not equal, false otherwise
	 */
	public static boolean numNotEquals(int a, int b) {
		return a != b;
	}

	/**
	 * Compares two integers using signed less than comparison.
	 *
	 * @param a first integer to compare
	 * @param b second integer to compare
	 * @return true if a is less than b, false otherwise
	 */
	public static boolean numLessThanSigned(int a, int b) {
		return a < b;
	}

	/**
	 * Compares two integers using unsigned less than comparison.
	 *
	 * @param a first integer to compare
	 * @param b second integer to compare
	 * @return true if a is less than b when treated as unsigned values, false otherwise
	 */
	public static boolean numLessThanUnsigned(int a, int b) {
		return Integer.compareUnsigned(a, b) < 0;
	}

	/**
	 * Compares two integers using signed greater than comparison.
	 *
	 * @param a first integer to compare
	 * @param b second integer to compare
	 * @return true if a is greater than b, false otherwise
	 */
	public static boolean numGreaterThanSigned(int a, int b) {
		return a > b;
	}

	/**
	 * Compares two integers using unsigned greater than comparison.
	 *
	 * @param a first integer to compare
	 * @param b second integer to compare
	 * @return true if a is greater than b when treated as unsigned values, false otherwise
	 */
	public static boolean numGreaterThanUnsigned(int a, int b) {
		return Integer.compareUnsigned(a, b) > 0;
	}

	/**
	 * Compares two integers using signed less than or equal comparison.
	 *
	 * @param a first integer to compare
	 * @param b second integer to compare
	 * @return true if a is less than or equal to b, false otherwise
	 */
	public static boolean numLessThanOrEqualSigned(int a, int b) {
		return a <= b;
	}

	/**
	 * Compares two integers using unsigned less than or equal comparison.
	 *
	 * @param a first integer to compare
	 * @param b second integer to compare
	 * @return true if a is less than or equal to b when treated as unsigned values, false otherwise
	 */
	public static boolean numLessThanOrEqualUnsigned(int a, int b) {
		return Integer.compareUnsigned(a, b) <= 0;
	}

	/**
	 * Compares two integers using signed greater than or equal comparison.
	 *
	 * @param a first integer to compare
	 * @param b second integer to compare
	 * @return true if a is greater than or equal to b, false otherwise
	 */
	public static boolean numGreaterThanOrEqualSigned(int a, int b) {
		return a >= b;
	}

	/**
	 * Compares two integers using unsigned greater than or equal comparison.
	 *
	 * @param a first integer to compare
	 * @param b second integer to compare
	 * @return true if a is greater than or equal to b when treated as unsigned values, false otherwise
	 */
	public static boolean numGreaterThanOrEqualUnsigned(int a, int b) {
		return Integer.compareUnsigned(a, b) >= 0;
	}

	/**
	 * Compares two long values for equality.
	 *
	 * @param a first long value to compare
	 * @param b second long value to compare
	 * @return true if the long values are equal, false otherwise
	 */
	public static boolean numEquals(long a, long b) {
		return a == b;
	}

	/**
	 * Checks if two long values are not equal.
	 *
	 * @param a first long value to compare
	 * @param b second long value to compare
	 * @return true if the long values are not equal, false otherwise
	 */
	public static boolean numNotEquals(long a, long b) {
		return a != b;
	}

	/**
	 * Compares two long values using signed less than comparison.
	 *
	 * @param a first long value to compare
	 * @param b second long value to compare
	 * @return true if a is less than b, false otherwise
	 */
	public static boolean numLessThanSigned(long a, long b) {
		return a < b;
	}

	/**
	 * Compares two long values using unsigned less than comparison.
	 *
	 * @param a first long value to compare
	 * @param b second long value to compare
	 * @return true if a is less than b when treated as unsigned values, false otherwise
	 */
	public static boolean numLessThanUnsigned(long a, long b) {
		return Long.compareUnsigned(a, b) < 0;
	}

	/**
	 * Compares two long values using signed greater than comparison.
	 *
	 * @param a first long value to compare
	 * @param b second long value to compare
	 * @return true if a is greater than b, false otherwise
	 */
	public static boolean numGreaterThanSigned(long a, long b) {
		return a > b;
	}

	/**
	 * Compares two long values using unsigned greater than comparison.
	 *
	 * @param a first long value to compare
	 * @param b second long value to compare
	 * @return true if a is greater than b when treated as unsigned values, false otherwise
	 */
	public static boolean numGreaterThanUnsigned(long a, long b) {
		return Long.compareUnsigned(a, b) > 0;
	}

	/**
	 * Compares two long values using signed less than or equal comparison.
	 *
	 * @param a first long value to compare
	 * @param b second long value to compare
	 * @return true if a is less than or equal to b, false otherwise
	 */
	public static boolean numLessThanOrEqualSigned(long a, long b) {
		return a <= b;
	}

	/**
	 * Compares two long values using unsigned less than or equal comparison.
	 *
	 * @param a first long value to compare
	 * @param b second long value to compare
	 * @return true if a is less than or equal to b when treated as unsigned values, false otherwise
	 */
	public static boolean numLessThanOrEqualUnsigned(long a, long b) {
		return Long.compareUnsigned(a, b) <= 0;
	}

	/**
	 * Compares two long values using signed greater than or equal comparison.
	 *
	 * @param a first long value to compare
	 * @param b second long value to compare
	 * @return true if a is greater than or equal to b, false otherwise
	 */
	public static boolean numGreaterThanOrEqualSigned(long a, long b) {
		return a >= b;
	}

	/**
	 * Compares two long values using unsigned greater than or equal comparison.
	 *
	 * @param a first long value to compare
	 * @param b second long value to compare
	 * @return true if a is greater than or equal to b when treated as unsigned values, false otherwise
	 */
	public static boolean numGreaterThanOrEqualUnsigned(long a, long b) {
		return Long.compareUnsigned(a, b) >= 0;
	}
}
