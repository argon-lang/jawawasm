package dev.argon.jawawasm.runtime.tests;

import dev.argon.jawawasm.runtime.V128;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class V128BuilderReplacementTest extends V128TestSupport {
	@Test
	public void splatsAndReplacementsPreserveLaneBits() {
		V128 original = new V128(0x7766554433221100L, 0xffeeddccbbaa9988L);
		for(int index = 0; index < 16; ++index) {
			V128 replaced = original.replaceLane8((byte)0xaa, index);
			for(int i = 0; i < 16; ++i)
				assertEquals(i == index ? (byte)0xaa : original.extractLane8(i), replaced.extractLane8(i));
		}
		for(int index = 0; index < 8; ++index) {
			V128 replaced = original.replaceLane16((short)0xaabb, index);
			for(int i = 0; i < 8; ++i)
				assertEquals(i == index ? (short)0xaabb : original.extractLane16(i), replaced.extractLane16(i));
		}
		for(int index = 0; index < 4; ++index) {
			V128 replaced = original.replaceLane32(0xaabbccdd, index);
			for(int i = 0; i < 4; ++i)
				assertEquals(i == index ? 0xaabbccdd : original.extractLane32(i), replaced.extractLane32(i));
		}
		for(int index = 0; index < 2; ++index) {
			V128 replaced = original.replaceLane64(0xaabbccddeeff0011L, index);
			for(int i = 0; i < 2; ++i)
				assertEquals(i == index ? 0xaabbccddeeff0011L : original.extractLane64(i), replaced.extractLane64(i));
		}
		for(int index : new int[]{-1, Integer.MIN_VALUE, Integer.MAX_VALUE, 16}) {
			assertEquals(original, original.replaceLane8((byte)1, index));
			assertEquals(original, original.replaceLane16((short)1, index));
			assertEquals(original, original.replaceLane32(1, index));
			assertEquals(original, original.replaceLane64(1, index));
		}
		for(int value : new int[]{0, 1, -1, 127, 128, 32768, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
			for(int i = 0; i < 16; ++i)
				assertEquals((byte)value, V128.splat8((byte)value).extractLane8(i));
			for(int i = 0; i < 8; ++i)
				assertEquals((short)value, V128.splat16((short)value).extractLane16(i));
			for(int i = 0; i < 4; ++i)
				assertEquals(value, V128.splat32(value).extractLane32(i));
		}
		assertEquals(new V128(Long.MIN_VALUE, Long.MIN_VALUE), V128.splat64(Long.MIN_VALUE));
		assertEquals(new V128(0x8000000080000000L, 0x8000000080000000L), V128.splatF32(-0.0f));
		assertEquals(new V128(Long.MIN_VALUE, Long.MIN_VALUE), V128.splatF64(-0.0));
	}

	@Test
	public void swizzleAndShuffleSelectBothHalvesAndZeroInvalidSwizzleIndices() {
		V128 a = bytePattern(0, 1), b = bytePattern(16, 1);
		V128 indexes = new V128(0x1f07100800180f17L, 0x0c1c15191e120e04L);
		V128 shuffled = a.shuffle8(b, indexes);
		for(int i = 0; i < 16; ++i)
			assertEquals(indexes.extractLane8(i), shuffled.extractLane8(i));
		for(int base = 0; base < 256; base += 16) {
			V128 indices = bytePattern(base, 1), swizzled = a.swizzle8(indices);
			for(int i = 0; i < 16; ++i)
				assertEquals(base + i < 16 ? (byte)(base + i) : (byte)0, swizzled.extractLane8(i));
		}
		assertThrows(IllegalArgumentException.class, () -> a.shuffle8(b, V128.splat8((byte)32)));
	}

	@Test
	public void wideningConstructorsPreserveSignednessAndOrder() {
		long bits = 0xffeeddccbbaa9988L;
		V128 s8 = V128.of8x8S(bits), u8 = V128.of8x8U(bits);
		for(int i = 0; i < 8; ++i) {
			assertEquals((short)(byte)(bits >>> (i * 8)), s8.extractLane16(i));
			assertEquals((short)((bits >>> (i * 8)) & 0xff), u8.extractLane16(i));
		}
		V128 s16 = V128.of16x4S(bits), u16 = V128.of16x4U(bits);
		for(int i = 0; i < 4; ++i) {
			assertEquals((int)(short)(bits >>> (i * 16)), s16.extractLane32(i));
			assertEquals((int)((bits >>> (i * 16)) & 0xffff), u16.extractLane32(i));
		}
		V128 s32 = V128.of32x2S(bits), u32 = V128.of32x2U(bits);
		for(int i = 0; i < 2; ++i) {
			assertEquals((long)(int)(bits >>> (i * 32)), s32.extractLane64(i));
			assertEquals((bits >>> (i * 32)) & 0xffffffffL, u32.extractLane64(i));
		}
	}

	@Test
	public void dotProductsMatchScalarLaneSums() {
		Random random = new Random(128);
		for(int iteration = 0; iteration < 1000; ++iteration) {
			V128 a = new V128(random.nextLong(), random.nextLong());
			V128 b = new V128(random.nextLong(), random.nextLong());
			V128 c = new V128(random.nextLong(), random.nextLong());
			V128 dot16 = a.dotS16x8(b), dot8 = a.relaxedDotI8x16ByI7x16Signed(b),
				dotAdd = a.relaxedDotI8x16ByI7x16AddSigned(b, c);
			for(int i = 0; i < 4; ++i) {
				assertEquals(
					a.extractLane16(2 * i) * b.extractLane16(2 * i)
						+ a.extractLane16(2 * i + 1) * b.extractLane16(2 * i + 1),
					dot16.extractLane32(i)
				);
				int sum = c.extractLane32(i);
				for(int j = 0; j < 4; ++j)
					sum += a.extractLane8(4 * i + j) * b.extractLane8(4 * i + j);
				assertEquals(sum, dotAdd.extractLane32(i));
			}
			for(int i = 0; i < 8; ++i) {
				int sum = a.extractLane8(2 * i) * b.extractLane8(2 * i)
					+ a.extractLane8(2 * i + 1) * b.extractLane8(2 * i + 1);
				assertEquals((short)Math.max(-32768, Math.min(32767, sum)), dot8.extractLane16(i));
			}
		}
	}
}
