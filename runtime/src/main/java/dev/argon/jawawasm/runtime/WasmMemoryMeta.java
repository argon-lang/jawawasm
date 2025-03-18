package dev.argon.jawawasm.runtime;

import org.jspecify.annotations.Nullable;

final class WasmMemoryMeta implements WasmMemory {

	public WasmMemoryMeta(MemoryAllocator allocator, @Nullable Long maxSize, WasmMemoryNoResize mem) {
		this.allocator = allocator;
		this.maxSize = maxSize;
		this.mem = mem;
	}

	private final MemoryAllocator allocator;
	private final @Nullable Long maxSize;
	private WasmMemoryNoResize mem;

	@Override
	public AddrType addressType() {
		return mem.addressType();
	}

	@Override
	public long byteSize() {
		return mem.byteSize();
	}

	@Override
	public long pageSize() {
		return mem.pageSize();
	}

	@Override
	public @Nullable Long maxPageSize() {
		return maxSize;
	}

	@Override
	public byte loadI8(long address) {
		return mem.loadI8(address);
	}

	@Override
	public short loadI16(long address) {
		return mem.loadI16(address);
	}

	@Override
	public int loadI32(long address) {
		return mem.loadI32(address);
	}

	@Override
	public long loadI64(long address) {
		return mem.loadI64(address);
	}

	@Override
	public float loadF32(long address) {
		return mem.loadF32(address);
	}

	@Override
	public double loadF64(long address) {
		return mem.loadF64(address);
	}

	@Override
	public V128 loadV128(long address) {
		return mem.loadV128(address);
	}

	@Override
	public void storeI8(long address, byte value) {
		mem.storeI8(address, value);
	}

	@Override
	public void storeI16(long address, short value) {
		mem.storeI16(address, value);
	}

	@Override
	public void storeI32(long address, int value) {
		mem.storeI32(address, value);
	}

	@Override
	public void storeI64(long address, long value) {
		mem.storeI64(address, value);
	}

	@Override
	public void storeF32(long address, float value) {
		mem.storeF32(address, value);
	}

	@Override
	public void storeF64(long address, double value) {
		mem.storeF64(address, value);
	}


	@Override
	public long grow(long pages) {
		long oldPages = mem.pageSize();
		var newPages = oldPages + pages;
		if(pages < 0 || newPages < 0 || (maxSize != null && maxSize < newPages)) {
			return -1;
		}

		long engineMemoryLimit = allocator.getMaxMemory();
		if(engineMemoryLimit > 0 && newPages > engineMemoryLimit) {
			return -1;
		}

		var newMem = allocator.allocateMemory(addressType(), newPages);
		newMem.copyFrom(mem);

		mem = newMem;
		return oldPages;
	}
}
