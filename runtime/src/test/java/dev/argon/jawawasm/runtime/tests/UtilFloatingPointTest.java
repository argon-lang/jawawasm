package dev.argon.jawawasm.runtime.tests;

import dev.argon.jawawasm.runtime.Util;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class UtilFloatingPointTest {
	@Test
	public void roundingPreservesZeroSignsAndExceptionalValues() {
		for(double[] sample : new double[][]{{-0.0, -0.0, -0.0}, {0.0, 0.0, 0.0},
				{Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY},
				{Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY},
				{-Double.MIN_VALUE, -0.0, -0.0}, {Double.MIN_VALUE, 0.0, 0.0}, {-0.25, -0.0, -0.0}, {0.25, 0.0, 0.0},
				{-0.5, -0.0, -0.0}, {0.5, 0.0, 0.0}, {-1.5, -1.0, -2.0}, {1.5, 1.0, 2.0}, {-2.5, -2.0, -2.0},
				{2.5, 2.0, 2.0}}) {
			assertDoubleBits(sample[1], Util.truncF64(sample[0]));
			assertDoubleBits(sample[2], Util.nearestF64(sample[0]));
			assertFloatBits((float)sample[1], Util.truncF32((float)sample[0]));
			assertFloatBits((float)sample[2], Util.nearestF32((float)sample[0]));
		}
		assertFloatBits(-0.0f, Util.truncF32(-Float.MIN_VALUE));
		assertFloatBits(-0.0f, Util.nearestF32(-Float.MIN_VALUE));
		assertDoubleBits(-0.0, Util.truncF64(-0.25));
		assertDoubleBits(-0.0, Util.nearestF64(-0.5));
		assertFloatBits(-0.0f, Util.truncF32(-0.25f));
		assertFloatBits(-0.0f, Util.nearestF32(-0.5f));
		assertDoubleBits(2.0, Util.nearestF64(2.5));
		assertFloatBits(2.0f, Util.nearestF32(1.5f));
	}

	@Test
	public void minAndMaxHandleBothOrdersOfSignedZero() {
		for(double[] sample : new double[][]{{-0.0, -0.0, -0.0, -0.0}, {-0.0, 0.0, -0.0, 0.0}, {0.0, -0.0, -0.0, 0.0},
				{0.0, 0.0, 0.0, 0.0}, {-2.0, 1.0, -2.0, 1.0}, {1.0, -2.0, -2.0, 1.0}, {Double.NEGATIVE_INFINITY,
						Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}}) {
			assertDoubleBits(sample[2], Util.min(sample[0], sample[1]));
			assertDoubleBits(sample[3], Util.max(sample[0], sample[1]));
			assertFloatBits((float)sample[2], Util.min((float)sample[0], (float)sample[1]));
			assertFloatBits((float)sample[3], Util.max((float)sample[0], (float)sample[1]));
		}
		assertDoubleBits(-0.0, Util.min(0.0, -0.0));
		assertDoubleBits(0.0, Util.max(-0.0, 0.0));
		assertFloatBits(-0.0f, Util.min(0.0f, -0.0f));
		assertFloatBits(0.0f, Util.max(-0.0f, 0.0f));
	}

	@Test
	public void operationsReturnCanonicalNaNBitsForEitherNaNOperand() {
		for(long bits : new long[]{0x7ff0000000000001L, 0x7ff8123456789abcL, 0xfff8123456789abcL}) {
			double nan = Double.longBitsToDouble(bits);
			assertDoubleBits(Double.NaN, Util.truncF64(nan));
			assertDoubleBits(Double.NaN, Util.nearestF64(nan));
			assertDoubleBits(Double.NaN, Util.min(nan, 1.0));
			assertDoubleBits(Double.NaN, Util.min(1.0, nan));
			assertDoubleBits(Double.NaN, Util.max(nan, 1.0));
			assertDoubleBits(Double.NaN, Util.max(1.0, nan));
		}
		for(int bits : new int[]{0x7f800001, 0x7fc12345, 0xffc12345}) {
			float nan = Float.intBitsToFloat(bits);
			assertFloatBits(Float.NaN, Util.truncF32(nan));
			assertFloatBits(Float.NaN, Util.nearestF32(nan));
			assertFloatBits(Float.NaN, Util.min(nan, 1.0f));
			assertFloatBits(Float.NaN, Util.min(1.0f, nan));
			assertFloatBits(Float.NaN, Util.max(nan, 1.0f));
			assertFloatBits(Float.NaN, Util.max(1.0f, nan));
		}
	}

	private static void assertDoubleBits(double expected, double actual) {
		assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual));
	}

	private static void assertFloatBits(float expected, float actual) {
		assertEquals(Float.floatToRawIntBits(expected), Float.floatToRawIntBits(actual));
	}
}
