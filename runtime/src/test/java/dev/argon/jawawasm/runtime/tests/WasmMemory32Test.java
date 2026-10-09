package dev.argon.jawawasm.runtime.tests;

import dev.argon.jawawasm.runtime.RuntimeOptions;
import dev.argon.jawawasm.runtime.V128;
import dev.argon.jawawasm.runtime.WasmMemory32;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WasmMemory32Test {
	private static WasmMemory32 memory(int pages, int maxPages) {
		return WasmMemory32.create(RuntimeOptions.builder().build(), pages, maxPages);
	}

	@Test
	public void accessesUnalignedLittleEndianValuesAndStaticOffsets() {
		var mem = memory(1, 2);
		WasmMemory32.storeI64(1, 0x0807060504030201L, 2, mem);
		assertEquals(1, mem.loadI8(3));
		assertEquals(0x0201, mem.loadI16(3));
		assertEquals(0x04030201, mem.loadI32(3));
		assertEquals(0x0807060504030201L, WasmMemory32.loadI64(1, 2, mem));
		WasmMemory32.storeF32(11, 1.25f, 2, mem);
		assertEquals(1.25f, WasmMemory32.loadF32(11, 2, mem));
		WasmMemory32.storeF64(17, -2.5, 2, mem);
		assertEquals(-2.5, WasmMemory32.loadF64(17, 2, mem));
		var vector = new V128(123L, 456L);
		WasmMemory32.storeV128(27, vector, 2, mem);
		assertEquals(vector, WasmMemory32.loadV128(27, 2, mem));
	}

	@Test
	public void bulkCopiesOverlapAndValidateBeforeWriting() {
		var mem = memory(1, 2);
		WasmMemory32.copyFromArray(0, 0, 6, new byte[]{1, 2, 3, 4, 5, 6}, mem);
		WasmMemory32.copy(1, 0, 5, mem, mem);
		byte[] result = new byte[6];
		mem.copyToArray(0, 0, 6, result);
		assertArrayEquals(new byte[]{1, 1, 2, 3, 4, 5}, result);
		WasmMemory32.copy(0, 1, 5, mem, mem);
		mem.copyToArray(0, 0, 6, result);
		assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 5}, result);
		var other = memory(0, 1);
		assertThrows(IndexOutOfBoundsException.class, () -> mem.copyFrom(0, 0, 1, other));
		assertEquals(1, mem.loadI8(0));
		WasmMemory32.fill(0, (byte)7, 6, mem);
		assertEquals(7, mem.loadI8(5));
	}

	@Test
	public void trapsUnsignedAddressesAndOffsetOverflowWithoutPartialVectorStores() {
		var mem = memory(1, 2);
		assertThrows(IndexOutOfBoundsException.class, () -> mem.loadI8(-1));
		assertThrows(IndexOutOfBoundsException.class, () -> WasmMemory32.storeI8(-1, (byte)1, 1, mem));
		assertThrows(IndexOutOfBoundsException.class, () -> WasmMemory32.loadI8(1, -1, mem));
		assertThrows(IndexOutOfBoundsException.class, () -> mem.storeV128(65528, new V128(1L, 2L)));
		assertEquals(0L, mem.loadI64(65528));
		assertThrows(IndexOutOfBoundsException.class, () -> mem.fill(0, (byte)1, -1));
	}

	@Test
	public void growthPreservesDataAndZeroesNewPages() {
		var mem = memory(1, 2);
		mem.storeI32(0, 123);
		assertEquals(1, WasmMemory32.grow(1, mem));
		assertEquals(2, mem.pageSize());
		assertEquals(131072L, mem.byteSize());
		assertEquals(2, mem.maxPageSize());
		assertEquals(123, mem.loadI32(0));
		assertEquals(0, mem.loadI32(65536));
		assertEquals(2, mem.grow(0));
		assertEquals(-1, mem.grow(1));
		assertEquals(-1, mem.grow(-1));
		assertEquals(-1, mem.grow(65536));
		assertThrows(IllegalArgumentException.class, () -> memory(65537, 65537));
		assertThrows(IllegalArgumentException.class, () -> memory(2, 1));
	}
}
