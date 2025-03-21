package dev.argon.jawawasm.runtime;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static java.nio.ByteOrder.LITTLE_ENDIAN;

class WasmMemoryImpl extends WasmMemoryNoResize {

	WasmMemoryImpl(AddrType addrType, MemorySegment mem) {
		this.addrType = addrType;
		this.mem = mem;
	}

	private final AddrType addrType;
	private final MemorySegment mem;

	@Override
	public AddrType addressType() {
		return addrType;
	}

	@Override
	public long byteSize() {
		return mem.byteSize();
	}

	@Override
	public long pageSize() {
		return mem.byteSize() / Util.PAGE_SIZE;
	}

	@Override
	public byte loadI8(long address) {
		return mem.get(ValueLayout.JAVA_BYTE, address);
	}
	@Override
	public short loadI16(long address) {
		return mem.get(ValueLayout.JAVA_SHORT.withOrder(LITTLE_ENDIAN).withByteAlignment(1), address);
	}
	@Override
	public int loadI32(long address) {
		return mem.get(ValueLayout.JAVA_INT.withOrder(LITTLE_ENDIAN).withByteAlignment(1), address);
	}
	@Override
	public long loadI64(long address) {
		return mem.get(ValueLayout.JAVA_LONG.withOrder(LITTLE_ENDIAN).withByteAlignment(1), address);
	}

	@Override
	public float loadF32(long address) {
		return mem.get(ValueLayout.JAVA_FLOAT.withOrder(LITTLE_ENDIAN).withByteAlignment(1), address);
	}
	@Override
	public double loadF64(long address) {
		return mem.get(ValueLayout.JAVA_DOUBLE.withOrder(LITTLE_ENDIAN).withByteAlignment(1), address);
	}

	@Override
	public void storeI8(long address, byte value) {
		mem.set(ValueLayout.JAVA_BYTE.withOrder(LITTLE_ENDIAN).withByteAlignment(1), address, value);
	}
	@Override
	public void storeI16(long address, short value) {
		mem.set(ValueLayout.JAVA_SHORT.withOrder(LITTLE_ENDIAN).withByteAlignment(1), address, value);
	}
	@Override
	public void storeI32(long address, int value) {
		mem.set(ValueLayout.JAVA_INT.withOrder(LITTLE_ENDIAN).withByteAlignment(1), address, value);
	}
	@Override
	public void storeI64(long address, long value) {
		mem.set(ValueLayout.JAVA_LONG.withOrder(LITTLE_ENDIAN).withByteAlignment(1), address, value);
	}

	@Override
	public void storeF32(long address, float value) {
		mem.set(ValueLayout.JAVA_FLOAT.withOrder(LITTLE_ENDIAN).withByteAlignment(1), address, value);
	}
	@Override
	public void storeF64(long address, double value) {
		mem.set(ValueLayout.JAVA_DOUBLE.withOrder(LITTLE_ENDIAN).withByteAlignment(1), address, value);
	}

	@Override
	public void fill(long d, byte val, long n) {
		mem.asSlice(d, n).fill(val);
	}

	@Override
	public void copyFrom(long d, long s, long n, WasmMemory srcMemory) {
		if(!(srcMemory instanceof WasmMemoryMeta srcMemory2)) {
			super.copyFrom(d, s, n, srcMemory);
			return;
		}

		if(!(srcMemory2.underlying() instanceof WasmMemoryImpl srcMemory3)) {
			super.copyFrom(d, s, n, srcMemory);
			return;
		}

		mem.asSlice(d, n).copyFrom(srcMemory3.mem.asSlice(s, n));
	}

	@Override
	public void copyFrom(WasmMemoryNoResize other) {
		var other2 = other;
		if(other instanceof WasmMemoryMeta metaMem) {
			other2 = metaMem.underlying();
		}

		if(!(other2 instanceof WasmMemoryImpl other3)) {
			super.copyFrom(other);
			return;
		}

		mem.copyFrom(other3.mem);
	}

	@Override
	public void copyFromArray(long address, int offset, int length, byte[] data) {
		var source = MemorySegment.ofArray(data);
		mem.asSlice(address, length).copyFrom(source.asSlice(offset, length));
	}

	@Override
	public void copyToArray(long address, int offset, int length, byte[] data) {
		var dest = MemorySegment.ofArray(data);
		dest.asSlice(offset, length).copyFrom(mem.asSlice(address, length));
	}


}
