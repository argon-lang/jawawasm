package dev.argon.jawawasm.runtime.tests;

import dev.argon.jawawasm.runtime.Util;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Random;
import java.util.function.DoubleToLongFunction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class UtilConversionTest {
	@Test
	public void signedTruncationChecksTheTruncatedValueAtBothBounds() {
		assertEquals(Integer.MIN_VALUE, Util.truncF64ToS32(-2147483648.75));
		assertEquals(Integer.MIN_VALUE, Util.truncF64ToS32(Math.nextUp(-2147483649.0)));
		assertEquals(Integer.MAX_VALUE, Util.truncF64ToS32(Math.nextDown(0x1p31)));
		assertThrows(ArithmeticException.class, () -> Util.truncF64ToS32(-2147483649.0));
		assertThrows(ArithmeticException.class, () -> Util.truncF64ToS32(0x1p31));
		assertEquals(Long.MIN_VALUE, Util.truncF64ToS64(-0x1p63));
		assertEquals(Long.MIN_VALUE + 1024, Util.truncF64ToS64(Math.nextUp(-0x1p63)));
		assertEquals(0x7ffffffffffffc00L, Util.truncF64ToS64(Math.nextDown(0x1p63)));
		assertThrows(ArithmeticException.class, () -> Util.truncF64ToS64(Math.nextDown(-0x1p63)));
		assertThrows(ArithmeticException.class, () -> Util.truncF64ToS64(0x1p63));
		for(double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
			assertThrows(ArithmeticException.class, () -> Util.truncF64ToS32(value));
			assertThrows(ArithmeticException.class, () -> Util.truncF64ToS64(value));
		}
	}

	@Test
	public void unsignedTruncationPreservesUpperHalfBitsAndAcceptsNegativeFractions() {
		for(double value : new double[] {-0.0, 0.0, -Double.MIN_VALUE, Math.nextUp(-1.0)}) {
			assertEquals(0, Util.truncF64ToU32(value));
			assertEquals(0L, Util.truncF64ToU64(value));
		}
		assertEquals(Integer.MIN_VALUE, Util.truncF64ToU32(0x1p31));
		assertEquals(-1, Util.truncF64ToU32(Math.nextDown(0x1p32)));
		assertEquals(0x7ffffffffffffc00L, Util.truncF64ToU64(Math.nextDown(0x1p63)));
		assertEquals(Long.MIN_VALUE, Util.truncF64ToU64(0x1p63));
		assertEquals(Long.MIN_VALUE + 2048, Util.truncF64ToU64(Math.nextUp(0x1p63)));
		assertEquals(-2048L, Util.truncF64ToU64(Math.nextDown(0x1p64)));
		assertThrows(ArithmeticException.class, () -> Util.truncF64ToU32(0x1p32));
		assertThrows(ArithmeticException.class, () -> Util.truncF64ToU64(0x1p64));
		for(double value : new double[] {-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
			assertThrows(ArithmeticException.class, () -> Util.truncF64ToU32(value));
			assertThrows(ArithmeticException.class, () -> Util.truncF64ToU64(value));
		}
	}

	@Test
	public void unsignedSaturationHandlesExceptionalValuesAndBothHalves() {
		for(double value : new double[] {Double.NaN, Double.NEGATIVE_INFINITY, -1.0, -0.0, 0.0}) {
			assertEquals(0L, Util.truncSatF64U64(value));
			assertEquals(0L, Util.truncSatF32U64((float)value));
		}
		assertEquals(Long.MIN_VALUE, Util.truncSatF64U64(0x1p63));
		assertEquals(Long.MIN_VALUE, Util.truncSatF32U64(0x1p63f));
		assertEquals(-2048L, Util.truncSatF64U64(Math.nextDown(0x1p64)));
		assertEquals(-0x10000000000L, Util.truncSatF32U64(Math.nextDown(0x1p64f)));
		for(double value : new double[] {0x1p64, Double.MAX_VALUE, Double.POSITIVE_INFINITY}) {
			assertEquals(-1L, Util.truncSatF64U64(value));
			assertEquals(-1L, Util.truncSatF32U64((float)value));
		}
	}

	@Test
	public void conversionsMatchAnExactIntegerOracleForRawFloatingPointInputs() {
		Random random = new Random(128);
		BigInteger min32 = BigInteger.valueOf(Integer.MIN_VALUE), max32 = BigInteger.valueOf(Integer.MAX_VALUE);
		BigInteger min64 = BigInteger.valueOf(Long.MIN_VALUE), max64 = BigInteger.valueOf(Long.MAX_VALUE);
		BigInteger maxU32 = BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE);
		BigInteger maxU64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
		for(int i = 0; i < 10000; ++i) {
			double input = Double.longBitsToDouble(random.nextLong());
			checkTruncation(input, min32, max32, 32, Util::truncF64ToS32);
			checkTruncation(input, min64, max64, 64, Util::truncF64ToS64);
			checkTruncation(input, BigInteger.ZERO, maxU32, 32, Util::truncF64ToU32);
			checkTruncation(input, BigInteger.ZERO, maxU64, 64, Util::truncF64ToU64);
			assertEquals(saturatedUnsigned(input, maxU64), Util.truncSatF64U64(input));
			assertEquals(saturatedUnsigned((float)input, maxU64), Util.truncSatF32U64((float)input));
		}
	}

	private static void checkTruncation(double input, BigInteger min, BigInteger max, int width,
			DoubleToLongFunction conversion) {
		if(!Double.isFinite(input)) {
			assertThrows(ArithmeticException.class, () -> conversion.applyAsLong(input));
			return;
		}
		BigInteger integer = new BigDecimal(input).toBigInteger();
		if(integer.compareTo(min) < 0 || integer.compareTo(max) > 0) {
			assertThrows(ArithmeticException.class, () -> conversion.applyAsLong(input));
		}
		else {
			assertEquals(width == 32 ? integer.intValue() : integer.longValue(), conversion.applyAsLong(input));
		}
	}

	private static long saturatedUnsigned(double input, BigInteger max) {
		if(Double.isNaN(input) || input <= 0) return 0L;
		if(input == Double.POSITIVE_INFINITY) return -1L;
		return new BigDecimal(input).toBigInteger().min(max).longValue();
	}

	@Test
	public void unsignedIntegerConversionRoundsTiesToEvenWithoutDoubleRounding() {
		// Odd half-way values expose a lost sticky bit in the upper unsigned range.
		for(long value : new long[] {0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE,
				Long.MIN_VALUE + (1L << 39) - 1, Long.MIN_VALUE + (1L << 39), Long.MIN_VALUE + (1L << 39) + 1,
				Long.MIN_VALUE + 1023, Long.MIN_VALUE + 1024, Long.MIN_VALUE + 1025}) {
			checkUnsignedRounding(value);
		}
		for(int shift = 0; shift < 64; ++shift) {
			for(long delta = -2; delta <= 2; ++delta) checkUnsignedRounding((1L << shift) + delta);
		}
		Random random = new Random(64);
		for(int i = 0; i < 100000; ++i) checkUnsignedRounding(random.nextLong());
		assertEquals(Float.floatToRawIntBits(Math.nextUp(0x1p63f)),
				Float.floatToRawIntBits(Util.u64ToF32(Long.MIN_VALUE + (1L << 39) + 1)));
		assertEquals(Double.doubleToRawLongBits(Math.nextUp(0x1p63)),
				Double.doubleToRawLongBits(Util.u64ToF64(Long.MIN_VALUE + 1025)));
	}

	private static void checkUnsignedRounding(long input) {
		BigInteger unsigned = BigInteger.valueOf(input).and(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE));
		assertEquals(Float.floatToRawIntBits(unsigned.floatValue()), Float.floatToRawIntBits(Util.u64ToF32(input)));
		assertEquals(Double.doubleToRawLongBits(unsigned.doubleValue()), Double.doubleToRawLongBits(Util.u64ToF64(input)));
	}
}
