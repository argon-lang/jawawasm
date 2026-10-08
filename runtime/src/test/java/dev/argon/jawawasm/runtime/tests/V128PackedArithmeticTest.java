package dev.argon.jawawasm.runtime.tests;

import dev.argon.jawawasm.runtime.V128;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class V128PackedArithmeticTest {
	@Test
	public void byteArithmeticExhaustsPairsWithoutCrossLaneCarries() {
		for(int a = 0; a < 256; ++a) {
			for(int b = 0; b < 256; ++b) {
				V128 left = V128TestSupport.bytePattern(a, 37);
				V128 right = V128TestSupport.bytePattern(b, 71);
				V128 sum = left.add8(right), difference = left.sub8(right), negative = left.neg8();
				for(int i = 0; i < 16; ++i) {
					assertEquals((byte)(left.extractLane8(i) + right.extractLane8(i)), sum.extractLane8(i));
					assertEquals((byte)(left.extractLane8(i) - right.extractLane8(i)), difference.extractLane8(i));
					assertEquals((byte)-left.extractLane8(i), negative.extractLane8(i));
				}
			}
		}
	}

	@Test
	public void widerArithmeticPreservesIndependentLanes() {
		Random random = new Random(0x128);
		for(int iteration = 0; iteration < 10000; ++iteration) {
			V128 a = new V128(random.nextLong(), random.nextLong());
			V128 b = new V128(random.nextLong(), random.nextLong());
			V128 sum16 = a.add16(b), sub16 = a.sub16(b), neg16 = a.neg16();
			for(int i = 0; i < 8; ++i) {
				assertEquals((short)(a.extractLane16(i) + b.extractLane16(i)), sum16.extractLane16(i));
				assertEquals((short)(a.extractLane16(i) - b.extractLane16(i)), sub16.extractLane16(i));
				assertEquals((short)-a.extractLane16(i), neg16.extractLane16(i));
			}
			V128 sum32 = a.add32(b), sub32 = a.sub32(b), neg32 = a.neg32();
			for(int i = 0; i < 4; ++i) {
				assertEquals(a.extractLane32(i) + b.extractLane32(i), sum32.extractLane32(i));
				assertEquals(a.extractLane32(i) - b.extractLane32(i), sub32.extractLane32(i));
				assertEquals(-a.extractLane32(i), neg32.extractLane32(i));
			}
		}
	}
}
