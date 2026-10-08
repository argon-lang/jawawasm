package dev.argon.jawawasm.runtime.tests;

import dev.argon.jawawasm.runtime.V128;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class V128UnaryTest {
	@Test
	public void nearestF32RoundsTiesToEvenRatherThanTruncating() {
		V128 input = new V128(0x402000003fc00000L, 0xc0200000bfc00000L);
		V128 expected = new V128(0x4000000040000000L, 0xc0000000c0000000L);
		assertEquals(expected, input.nearestF32());
	}

	@Test
	public void packedUnaryOperationsPreserveLaneBoundaries() {
		Random random = new Random(128);
		int[] amounts = {-129, -65, -33, -17, -9, -1, 0, 1, 7, 8, 15, 16, 31, 32, 63, 64, 129, Integer.MIN_VALUE, Integer.MAX_VALUE};
		for(int iteration = 0; iteration < 256; ++iteration) {
			V128 a = V128TestSupport.bytePattern(iteration, 37);
			V128 abs = a.abs8(), pop = a.popcnt8();
			for(int i = 0; i < 16; ++i) {
				assertEquals((byte)Math.abs(a.extractLane8(i)), abs.extractLane8(i));
				assertEquals((byte)Integer.bitCount(Byte.toUnsignedInt(a.extractLane8(i))), pop.extractLane8(i));
			}
			V128 wide = iteration == 0 ? new V128(0x8000800080008000L, Long.MIN_VALUE) : new V128(random.nextLong(), random.nextLong());
			V128 abs16 = wide.abs16(), abs32 = wide.abs32(), abs64 = wide.abs64();
			for(int i = 0; i < 8; ++i) assertEquals((short)Math.abs(wide.extractLane16(i)), abs16.extractLane16(i));
			for(int i = 0; i < 4; ++i) assertEquals(Math.abs(wide.extractLane32(i)), abs32.extractLane32(i));
			for(int i = 0; i < 2; ++i) assertEquals(Math.abs(wide.extractLane64(i)), abs64.extractLane64(i));
			for(int amount : amounts) {
				V128 l8 = a.shl8(amount), u8 = a.shrU8(amount), s8 = a.shrS8(amount);
				for(int i = 0; i < 16; ++i) {
					byte x = a.extractLane8(i);
					assertEquals((byte)(x << (amount & 7)), l8.extractLane8(i));
					assertEquals((byte)(Byte.toUnsignedInt(x) >>> (amount & 7)), u8.extractLane8(i));
					assertEquals((byte)(x >> (amount & 7)), s8.extractLane8(i));
				}
				V128 l16 = wide.shl16(amount), u16 = wide.shrU16(amount), s16 = wide.shrS16(amount);
				for(int i = 0; i < 8; ++i) {
					short x = wide.extractLane16(i);
					assertEquals((short)(x << (amount & 15)), l16.extractLane16(i));
					assertEquals((short)(Short.toUnsignedInt(x) >>> (amount & 15)), u16.extractLane16(i));
					assertEquals((short)(x >> (amount & 15)), s16.extractLane16(i));
				}
				V128 l32 = wide.shl32(amount), u32 = wide.shrU32(amount), s32 = wide.shrS32(amount);
				for(int i = 0; i < 4; ++i) {
					int x = wide.extractLane32(i);
					assertEquals(x << amount, l32.extractLane32(i));
					assertEquals(x >>> amount, u32.extractLane32(i));
					assertEquals(x >> amount, s32.extractLane32(i));
				}
				V128 l64 = wide.shl64(amount), u64 = wide.shrU64(amount), s64 = wide.shrS64(amount);
				for(int i = 0; i < 2; ++i) {
					long x = wide.extractLane64(i);
					assertEquals(x << amount, l64.extractLane64(i));
					assertEquals(x >>> amount, u64.extractLane64(i));
					assertEquals(x >> amount, s64.extractLane64(i));
				}
			}
		}
	}
}
