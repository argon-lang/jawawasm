package dev.argon.jawawasm.runtime.tests;

import dev.argon.jawawasm.runtime.V128;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class V128TernaryTest {
	@Test
	public void relaxedFloatArithmeticPreservesScalarRounding() {
		int[] edges = {0, 0x80000000, 1, 0x80000001, 0x007fffff, 0x00800000,
			0x3f800000, 0xbf800000, 0x3f800001, 0x7f7fffff, 0x7f800000, 0xff800000, 0x7fc12345};
		Random random = new Random(128);
		for(int iteration = 0; iteration < edges.length * edges.length * edges.length + 10000; ++iteration) {
			int n = iteration;
			boolean edge = n < edges.length * edges.length * edges.length;
			int x = edge ? edges[n % edges.length] : random.nextInt(); n /= edges.length;
			int y = edge ? edges[n % edges.length] : random.nextInt(); n /= edges.length;
			int z = edge ? edges[n % edges.length] : random.nextInt();
			V128 a = new V128(Integer.toUnsignedLong(x) | ((long)y << 32), Integer.toUnsignedLong(z) | ((long)x << 32));
			V128 b = new V128(Integer.toUnsignedLong(y) | ((long)z << 32), Integer.toUnsignedLong(x) | ((long)y << 32));
			V128 c = new V128(Integer.toUnsignedLong(z) | ((long)x << 32), Integer.toUnsignedLong(y) | ((long)z << 32));
			V128 add = a.relaxedMaddF32(b, c), neg = a.relaxedNmaddF32(b, c);
			for(int lane = 0; lane < 4; ++lane) {
				float ai = a.extractLaneF32(lane), bi = b.extractLaneF32(lane), ci = c.extractLaneF32(lane);
				assertFloat(ai * bi + ci, add.extractLaneF32(lane));
				assertFloat(-(ai * bi) + ci, neg.extractLaneF32(lane));
			}
		}
	}

	@Test
	public void relaxedDoubleArithmeticPreservesScalarRounding() {
		long[] edges = {0, Long.MIN_VALUE, 1, Long.MIN_VALUE | 1, 0x000fffffffffffffL,
			0x0010000000000000L, 0x3ff0000000000000L, 0xbff0000000000000L,
			0x3ff0000000000001L, 0x7fefffffffffffffL, 0x7ff0000000000000L, 0xfff0000000000000L, 0x7ff8123456789abcL};
		Random random = new Random(128);
		for(int iteration = 0; iteration < edges.length * edges.length * edges.length + 10000; ++iteration) {
			int n = iteration;
			boolean edge = n < edges.length * edges.length * edges.length;
			long x = edge ? edges[n % edges.length] : random.nextLong(); n /= edges.length;
			long y = edge ? edges[n % edges.length] : random.nextLong(); n /= edges.length;
			long z = edge ? edges[n % edges.length] : random.nextLong();
			V128 a = new V128(x, y), b = new V128(y, z), c = new V128(z, x);
			V128 add = a.relaxedMaddF64(b, c), neg = a.relaxedNmaddF64(b, c);
			for(int lane = 0; lane < 2; ++lane) {
				double ai = a.extractLaneF64(lane), bi = b.extractLaneF64(lane), ci = c.extractLaneF64(lane);
				assertDouble(ai * bi + ci, add.extractLaneF64(lane));
				assertDouble(-(ai * bi) + ci, neg.extractLaneF64(lane));
			}
		}
	}

	private static void assertFloat(float expected, float actual) {
		if(Float.isNaN(expected)) assertTrue(Float.isNaN(actual));
		else assertEquals(Float.floatToRawIntBits(expected), Float.floatToRawIntBits(actual));
	}

	private static void assertDouble(double expected, double actual) {
		if(Double.isNaN(expected)) assertTrue(Double.isNaN(actual));
		else assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual));
	}
}
