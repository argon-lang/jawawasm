package dev.argon.jawawasm.runtime.tests;

import dev.argon.jawawasm.runtime.V128;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class V128BinaryTest {
	@Test
	public void byteAverageAndSaturationExhaustPairsAcrossLaneBoundaries() {
		for(int x = 0; x < 256; ++x) {
			for(int y = 0; y < 256; ++y) {
				V128 a = V128TestSupport.bytePattern(x, 37);
				V128 b = V128TestSupport.bytePattern(y, 71);
				V128 avg = a.avgrU8(b), add = a.addSatU8(b), sub = a.subSatU8(b);
				V128 signedAdd = a.addSatS8(b), signedSub = a.subSatS8(b);
				for(int i = 0; i < 16; ++i) {
					int ai = Byte.toUnsignedInt(a.extractLane8(i)), bi = Byte.toUnsignedInt(b.extractLane8(i));
					assertEquals((byte)((ai + bi + 1) / 2), avg.extractLane8(i));
					assertEquals((byte)Math.min(255, ai + bi), add.extractLane8(i));
					assertEquals((byte)Math.max(0, ai - bi), sub.extractLane8(i));
					int as = a.extractLane8(i), bs = b.extractLane8(i);
					assertEquals((byte)Math.max(-128, Math.min(127, as + bs)), signedAdd.extractLane8(i));
					assertEquals((byte)Math.max(-128, Math.min(127, as - bs)), signedSub.extractLane8(i));
				}
			}
		}
	}

	@Test
	public void shortAverageAndSaturationPreserveIndependentLanes() {
		Random random = new Random(128);
		for(int iteration = 0; iteration < 10000; ++iteration) {
			V128 a = new V128(random.nextLong(), random.nextLong());
			V128 b = new V128(random.nextLong(), random.nextLong());
			V128 avg = a.avgrU16(b), add = a.addSatU16(b), sub = a.subSatU16(b);
			V128 signedAdd = a.addSatS16(b), signedSub = a.subSatS16(b);
			for(int i = 0; i < 8; ++i) {
				int ai = Short.toUnsignedInt(a.extractLane16(i)), bi = Short.toUnsignedInt(b.extractLane16(i));
				assertEquals((short)((ai + bi + 1) / 2), avg.extractLane16(i));
				assertEquals((short)Math.min(65535, ai + bi), add.extractLane16(i));
				assertEquals((short)Math.max(0, ai - bi), sub.extractLane16(i));
				int as = a.extractLane16(i), bs = b.extractLane16(i);
				assertEquals((short)Math.max(-32768, Math.min(32767, as + bs)), signedAdd.extractLane16(i));
				assertEquals((short)Math.max(-32768, Math.min(32767, as - bs)), signedSub.extractLane16(i));
			}
		}
	}
}
