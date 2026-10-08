package dev.argon.jawawasm.runtime.tests;

import dev.argon.jawawasm.runtime.V128;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class V128ReductionTest {
	@Test
	public void byteZeroDetectionHandlesBorrowAcrossAdjacentLanes() {
		for(int pair = 0; pair < 65536; ++pair) {
			long bits = 0x0101010101010000L | pair;
			boolean expected = (pair & 255) != 0 && (pair >>> 8) != 0;
			assertEquals(expected, new V128(bits, -1L).allTrue8());
			assertEquals(expected, new V128(-1L, bits).allTrue8());
		}
	}

	@Test
	public void reductionsMatchScalarLanesIncludingZeroAndSignBits() {
		Random random = new Random(128);
		for(int iteration = 0; iteration < 10000; ++iteration) {
			V128 vector = new V128(random.nextLong(), random.nextLong());
			check(vector);
			check(vector.replaceLane16((short)0, iteration & 7));
			check(vector.replaceLane32(0, iteration & 3));
			check(vector.replaceLane64(0L, iteration & 1));
		}
		check(new V128(0L, 0L));
		check(new V128(Long.MIN_VALUE, Long.MIN_VALUE));
	}

	private static void check(V128 vector) {
		boolean any = false, all8 = true, all16 = true, all32 = true, all64 = true;
		int mask8 = 0, mask16 = 0, mask32 = 0, mask64 = 0;
		for(int i = 0; i < 16; ++i) {
			byte lane = vector.extractLane8(i);
			any |= lane != 0;
			all8 &= lane != 0;
			if(lane < 0) mask8 |= 1 << i;
		}
		for(int i = 0; i < 8; ++i) {
			short lane = vector.extractLane16(i);
			all16 &= lane != 0;
			if(lane < 0) mask16 |= 1 << i;
		}
		for(int i = 0; i < 4; ++i) {
			int lane = vector.extractLane32(i);
			all32 &= lane != 0;
			if(lane < 0) mask32 |= 1 << i;
		}
		for(int i = 0; i < 2; ++i) {
			long lane = vector.extractLane64(i);
			all64 &= lane != 0;
			if(lane < 0) mask64 |= 1 << i;
		}
		assertEquals(any, vector.anyTrue());
		assertEquals(all8, vector.allTrue8());
		assertEquals(all16, vector.allTrue16());
		assertEquals(all32, vector.allTrue32());
		assertEquals(all64, vector.allTrue64());
		assertEquals(mask8, vector.bitmask8());
		assertEquals(mask16, vector.bitmask16());
		assertEquals(mask32, vector.bitmask32());
		assertEquals(mask64, vector.bitmask64());
	}
}
