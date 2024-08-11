package dev.argon.jawawasm.engine;

import dev.argon.jawawasm.format.data.V128;
import dev.argon.jawawasm.format.types.MemType;
import dev.argon.jawawasm.format.types.Limits;

final class WasmMemoryMeta implements WasmMemory {

	public WasmMemoryMeta(Engine engine, Integer maxSize, WasmMemoryNoResize mem) {
		this.engine = engine;
		this.maxSize = maxSize;
		this.mem = mem;
	}

	private final Engine engine;
	private final Integer maxSize;
	private WasmMemoryNoResize mem;

	@Override
	public MemType type() {
		return new MemType(new Limits(mem.pageSize(), maxSize));
	}

	@Override
	public int byteSize() {
		return mem.byteSize();
	}

	@Override
	public int pageSize() {
		return mem.pageSize();
	}

	@Override
	public byte loadI8(int address) {
		return mem.loadI8(address);
	}

	@Override
	public short loadI16(int address) {
		return mem.loadI16(address);
	}

	@Override
	public int loadI32(int address) {
		return mem.loadI32(address);
	}

	@Override
	public long loadI64(int address) {
		return mem.loadI64(address);
	}

	@Override
	public float loadF32(int address) {
		return mem.loadF32(address);
	}

	@Override
	public double loadF64(int address) {
		return mem.loadF64(address);
	}

	@Override
	public V128 loadV128(int address) {
		return mem.loadV128(address);
	}

	@Override
	public void storeI8(int address, byte value) {
		mem.storeI8(address, value);
	}

	@Override
	public void storeI16(int address, short value) {
		mem.storeI16(address, value);
	}

	@Override
	public void storeI32(int address, int value) {
		mem.storeI32(address, value);
	}

	@Override
	public void storeI64(int address, long value) {
		mem.storeI64(address, value);
	}

	@Override
	public void storeF32(int address, float value) {
		mem.storeF32(address, value);
	}

	@Override
	public void storeF64(int address, double value) {
		mem.storeF64(address, value);
	}


	@Override
	public int grow(int pages) {
		int oldPages = mem.pageSize();
		var newPages = oldPages + pages;
		if(pages < 0 || newPages < 0 || (maxSize != null && maxSize < newPages)) {
			return -1;
		}

		int engineMemoryLimit = engine.getMaxMemory();
		if(engineMemoryLimit > 0 && newPages > engineMemoryLimit) {
			return -1;
		}

		var newMem = engine.allocateMemory(newPages);
		for(int address = 0; address < mem.byteSize(); address += 8) {
			newMem.storeI64(address, mem.loadI64(address));
		}

		mem = newMem;
		return oldPages;
	}
}
