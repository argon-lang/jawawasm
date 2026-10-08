package dev.argon.jawawasm.runtime.tests;

import dev.argon.jawawasm.runtime.V128;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class V128ConstructionTest extends V128TestSupport {
	@Test
	public void constructorPreservesBothHalves() {
		var value = new V128(0x7766554433221100L, 0xffeeddccbbaa9988L);
		assertEquals(0x7766554433221100L, value.low());
		assertEquals(0xffeeddccbbaa9988L, value.high());
		assertVector(0x7766554433221100L, 0xffeeddccbbaa9988L, value);
		assertEquals((byte)0x88, value.extractLane8(8));
		assertEquals((short)0x9988, value.extractLane16(4));
		assertEquals(0xbbaa9988, value.extractLane32(2));
	}

	@Test
	public void constructionPreservesLittleEndianLaneOrder() {
		var bytes = bytePattern(0, 0x11);
		assertVector(0x7766554433221100L, 0xffeeddccbbaa9988L, bytes);
		var longs = new V128(0x7766554433221100L, 0xffeeddccbbaa9988L);
		for(int i = 0; i < 16; ++i) {
			assertEquals((byte)(i * 0x11), longs.extractLane8(i));
		}
		for(int i = 0; i < 8; ++i) {
			assertEquals((short)(0x1100 + i * 0x2222), longs.extractLane16(i));
		}
		for(int i = 0; i < 4; ++i) {
			assertEquals(0x33221100 + i * 0x44444444, longs.extractLane32(i));
		}
	}

	@Test
	public void floatingPointLaneReplacementPreservesRawBits() {
		int[] floats = {0, 0x80000000, 0x7f800001, 0xffc12345};
		var f32 = V128.ZERO;
		for(int i = 0; i < floats.length; ++i) f32 = f32.replaceLaneF32(Float.intBitsToFloat(floats[i]), i);
		for(int i = 0; i < floats.length; ++i) {
			assertEquals(floats[i], Float.floatToRawIntBits(f32.extractLaneF32(i)));
		}
		long[] doubles = {0x8000000000000000L, 0x7ff0000000000001L};
		var f64 = V128.ZERO;
		for(int i = 0; i < doubles.length; ++i) f64 = f64.replaceLaneF64(Double.longBitsToDouble(doubles[i]), i);
		for(int i = 0; i < doubles.length; ++i) {
			assertEquals(doubles[i], Double.doubleToRawLongBits(f64.extractLaneF64(i)));
		}
	}

	@Test
	public void extractRejectsOutOfRangeLaneIndices() {
		for(int i : new int[] {-1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
			assertThrows(IllegalArgumentException.class, () -> V128.ZERO.extractLane8(i));
			assertThrows(IllegalArgumentException.class, () -> V128.ZERO.extractLane16(i));
			assertThrows(IllegalArgumentException.class, () -> V128.ZERO.extractLane32(i));
			assertThrows(IllegalArgumentException.class, () -> V128.ZERO.extractLane64(i));
		}
		assertThrows(IllegalArgumentException.class, () -> V128.ZERO.extractLane8(16));
		assertThrows(IllegalArgumentException.class, () -> V128.ZERO.extractLane16(8));
		assertThrows(IllegalArgumentException.class, () -> V128.ZERO.extractLane32(4));
		assertThrows(IllegalArgumentException.class, () -> V128.ZERO.extractLane64(2));
	}
}
