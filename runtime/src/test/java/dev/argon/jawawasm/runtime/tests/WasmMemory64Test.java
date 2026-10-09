package dev.argon.jawawasm.runtime.tests;

import dev.argon.jawawasm.runtime.RuntimeOptions;
import dev.argon.jawawasm.runtime.V128;
import dev.argon.jawawasm.runtime.WasmMemory64;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WasmMemory64Test {
	private static WasmMemory64 memory(long pages, long maxPages) {
		return WasmMemory64.create(RuntimeOptions.builder().build(), pages, maxPages);
	}

	@Test
	public void accessesUnalignedLittleEndianValuesAndStaticOffsets() {
		var mem = memory(1, 2);
		WasmMemory64.storeI64(1, 0x0807060504030201L, 2, mem);
		assertEquals(1, mem.loadI8(3));
		assertEquals(0x0201, mem.loadI16(3));
		assertEquals(0x04030201, mem.loadI32(3));
		assertEquals(0x0807060504030201L, WasmMemory64.loadI64(1, 2, mem));
		WasmMemory64.storeF32(11, 1.25f, 2, mem);
		assertEquals(1.25f, WasmMemory64.loadF32(11, 2, mem));
		WasmMemory64.storeF64(17, -2.5, 2, mem);
		assertEquals(-2.5, WasmMemory64.loadF64(17, 2, mem));
		var vector = new V128(123L, 456L);
		WasmMemory64.storeV128(27, vector, 2, mem);
		assertEquals(vector, WasmMemory64.loadV128(27, 2, mem));
	}

	@Test
	public void bulkCopiesOverlapAndValidateBeforeWriting() {
		var mem = memory(1, 2);
		WasmMemory64.copyFromArray(0, 0, 6, new byte[]{1, 2, 3, 4, 5, 6}, mem);
		WasmMemory64.copy(1, 0, 5, mem, mem);
		byte[] result = new byte[6];
		mem.copyToArray(0, 0, 6, result);
		assertArrayEquals(new byte[]{1, 1, 2, 3, 4, 5}, result);
		WasmMemory64.copy(0, 1, 5, mem, mem);
		mem.copyToArray(0, 0, 6, result);
		assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 5}, result);
		var other = memory(0, 1);
		assertThrows(IndexOutOfBoundsException.class, () -> mem.copyFrom(0, 0, 1, other));
		assertEquals(1, mem.loadI8(0));
		WasmMemory64.fill(0, (byte)7, 6, mem);
		assertEquals(7, mem.loadI8(5));
	}

	@Test
	public void trapsUnsignedAddressesAndOffsetOverflowWithoutPartialVectorStores() {
		var mem = memory(1, 2);
		assertThrows(IndexOutOfBoundsException.class, () -> mem.loadI8(-1));
		assertThrows(IndexOutOfBoundsException.class, () -> WasmMemory64.storeI8(-1, (byte)1, 1, mem));
		assertThrows(IndexOutOfBoundsException.class, () -> WasmMemory64.loadI8(1, -1, mem));
		assertThrows(IndexOutOfBoundsException.class, () -> mem.storeV128(65528, new V128(1L, 2L)));
		assertEquals(0L, mem.loadI64(65528));
		assertThrows(IndexOutOfBoundsException.class, () -> mem.fill(0, (byte)1, -1));
	}

	@Test
	public void growthPreservesDataAndZeroesNewPages() {
		var mem = memory(1, 2);
		mem.storeI32(0, 123);
		assertEquals(1, WasmMemory64.grow(1, mem));
		assertEquals(2, mem.pageSize());
		assertEquals(131072L, mem.byteSize());
		assertEquals(2L, mem.maxPageSize());
		assertEquals(123, mem.loadI32(0));
		assertEquals(0, mem.loadI32(65536));
		assertEquals(2, mem.grow(0));
		assertEquals(-1, mem.grow(1));
		assertEquals(-1, mem.grow(-1));
		assertEquals(-1, mem.grow(Long.MAX_VALUE));
		assertThrows(
			IllegalArgumentException.class,
			() -> memory(Long.MAX_VALUE / 65536 + 1, Long.MAX_VALUE / 65536 + 1)
		);
		assertThrows(IllegalArgumentException.class, () -> memory(2, 1));
	}

	@Test
	public void rejects64BitOverflowAndAddressesBeyond32Bits() {
		var mem = memory(1, 2);
		assertThrows(IndexOutOfBoundsException.class, () -> mem.loadI8(1L << 32));
		assertThrows(IndexOutOfBoundsException.class, () -> WasmMemory64.loadI8(1L << 32, 0, mem));
		assertThrows(IndexOutOfBoundsException.class, () -> WasmMemory64.loadI8(Long.MAX_VALUE, 1, mem));
		assertThrows(
			IndexOutOfBoundsException.class,
			() -> WasmMemory64.storeI8(Long.MIN_VALUE, (byte)1, Long.MIN_VALUE, mem)
		);
		assertThrows(IndexOutOfBoundsException.class, () -> mem.copyFrom(0, 0, Long.MIN_VALUE, mem));
		assertEquals(0, mem.loadI8(0));
		assertEquals(-1, mem.grow(Long.MIN_VALUE));
		assertEquals(-1, mem.grow(Long.MAX_VALUE));
		assertEquals(1, mem.pageSize());
	}

	@Test
	public void honorsRuntimeLimitsAndCopiesWholeMemories() {
		var options = RuntimeOptions.builder().maxMemory(2).build();
		assertThrows(IllegalArgumentException.class, () -> WasmMemory64.create(options, 3, null));
		var src = WasmMemory64.create(options, 1, null);
		var dst = WasmMemory64.create(options, 2, null);
		src.storeI32(0, 123);
		dst.copyFrom(src);
		assertEquals(123, dst.loadI32(0));
		assertThrows(IndexOutOfBoundsException.class, () -> src.copyFrom(dst));
		assertEquals(1, src.grow(1));
		assertEquals(-1, src.grow(1));
		assertEquals(dev.argon.jawawasm.runtime.AddrType.I64, src.addressType());
		src.ensureMinimumSize(2);
		assertThrows(dev.argon.jawawasm.runtime.ModuleLinkException.class, () -> src.ensureMinimumSize(-1));
	}

	@Test
	public void declaredMaximumCanExceedJavaAllocationLimit() {
		var mem = memory(0, 1L << 48);
		assertEquals(1L << 48, mem.maxPageSize());
		assertEquals(-1, mem.grow(Long.MAX_VALUE / 65536 + 1));
		assertThrows(IllegalArgumentException.class, () -> memory(0, (1L << 48) + 1));
	}
}
