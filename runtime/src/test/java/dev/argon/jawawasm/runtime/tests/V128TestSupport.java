package dev.argon.jawawasm.runtime.tests;

import dev.argon.jawawasm.runtime.V128;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Representation-independent construction and bit-exact SIMD assertions. */
public abstract class V128TestSupport {
	public static V128 bytePattern(int base, int stride) {
		long low = 0, high = 0;
		for(int i = 0; i < 8; ++i) {
			low |= ((base + i * stride) & 0xffL) << (i * 8);
			high |= ((base + (i + 8) * stride) & 0xffL) << (i * 8);
		}
		return new V128(low, high);
	}

	public static V128 vector(long low, long high) {
		return new V128(low, high);
	}

	public static void assertVector(long low, long high, V128 actual) {
		assertEquals(low, actual.extractLane64(0), "Low 64 bits");
		assertEquals(high, actual.extractLane64(1), "High 64 bits");
	}

	public static void assertVector(long low, long high, V128 actual, int width, int canonicalMask, int arithmeticMask) {
		long mask = width == 64 ? -1L : (1L << width) - 1;
		long canonical = width == 32 ? 0x7fc00000L : 0x7ff8000000000000L;
		for(int i = 0; i < 128 / width; ++i) {
			int shift = i * width % 64;
			long bits = (actual.extractLane64(i * width / 64) >>> shift) & mask;
			if((canonicalMask & (1 << i)) != 0) {
				assertEquals(canonical, bits & (width == 32 ? 0x7fffffffL : Long.MAX_VALUE), "Canonical NaN lane " + i);
			}
			else if((arithmeticMask & (1 << i)) != 0) {
				assertEquals(canonical, bits & canonical, "Arithmetic NaN lane " + i);
			}
			else {
				assertEquals(((i * width < 64 ? low : high) >>> shift) & mask, bits, "Lane " + i);
			}
		}
	}
}
