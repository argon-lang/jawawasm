package dev.argon.jawawasm.engine;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static java.nio.ByteOrder.LITTLE_ENDIAN;

class WasmMemoryImpl implements WasmMemoryNoResize {

	WasmMemoryImpl(MemorySegment mem) {
		this.mem = mem;
	}

	private final MemorySegment mem;

	@Override
	public int byteSize() {
		return (int)mem.byteSize();
	}

	@Override
	public int pageSize() {
		return (int)(mem.byteSize() / Util.PAGE_SIZE);
	}

	@Override
	public byte loadI8(int address) {
		return mem.get(ValueLayout.JAVA_BYTE, address);
	}
	@Override
	public short loadI16(int address) {
		return mem.get(ValueLayout.JAVA_SHORT.withOrder(LITTLE_ENDIAN).withByteAlignment(1), address);
	}
	@Override
	public int loadI32(int address) {
		return mem.get(ValueLayout.JAVA_INT.withOrder(LITTLE_ENDIAN).withByteAlignment(1), address);
	}
	@Override
	public long loadI64(int address) {
		return mem.get(ValueLayout.JAVA_LONG.withOrder(LITTLE_ENDIAN).withByteAlignment(1), address);
	}

	@Override
	public float loadF32(int address) {
		return mem.get(ValueLayout.JAVA_FLOAT.withOrder(LITTLE_ENDIAN).withByteAlignment(1), address);
	}
	@Override
	public double loadF64(int address) {
		return mem.get(ValueLayout.JAVA_DOUBLE.withOrder(LITTLE_ENDIAN).withByteAlignment(1), address);
	}

	@Override
	public void storeI8(int address, byte value) {
		mem.set(ValueLayout.JAVA_BYTE.withOrder(LITTLE_ENDIAN).withByteAlignment(1), address, value);
	}
	@Override
	public void storeI16(int address, short value) {
		mem.set(ValueLayout.JAVA_SHORT.withOrder(LITTLE_ENDIAN).withByteAlignment(1), address, value);
	}
	@Override
	public void storeI32(int address, int value) {
		mem.set(ValueLayout.JAVA_INT.withOrder(LITTLE_ENDIAN).withByteAlignment(1), address, value);
	}
	@Override
	public void storeI64(int address, long value) {
		mem.set(ValueLayout.JAVA_LONG.withOrder(LITTLE_ENDIAN).withByteAlignment(1), address, value);
	}

	@Override
	public void storeF32(int address, float value) {
		mem.set(ValueLayout.JAVA_FLOAT.withOrder(LITTLE_ENDIAN).withByteAlignment(1), address, value);
	}
	@Override
	public void storeF64(int address, double value) {
		mem.set(ValueLayout.JAVA_DOUBLE.withOrder(LITTLE_ENDIAN).withByteAlignment(1), address, value);
	}

	@Override
	public void copyFromArray(int address, int offset, int length, byte[] data) {
		var source = MemorySegment.ofArray(data);
		mem.asSlice(address, length).copyFrom(source.asSlice(offset, length));
	}

	@Override
	public void copyToArray(int address, int offset, int length, byte[] data) {
		var dest = MemorySegment.ofArray(data);
		dest.asSlice(offset, length).copyFrom(mem.asSlice(address, length));
	}
}
