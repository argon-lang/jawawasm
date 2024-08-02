package jvmwasm.engine;

import jvmwasm.format.data.V128;
import jvmwasm.format.modules.Data;

import java.lang.foreign.ValueLayout;

public interface WasmMemoryNoResize {

	int byteSize();

	int pageSize();

	byte loadI8(int address) throws Throwable;

	short loadI16(int address) throws Throwable;

	int loadI32(int address) throws Throwable;

	long loadI64(int address) throws Throwable;

	float loadF32(int address) throws Throwable;

	double loadF64(int address) throws Throwable;

	default V128 loadV128(int address) throws Throwable {
		int[] values = new int[4];
		for(int i = 0; i < values.length; ++i) {
			values[i] = loadI32(address + i * 4);
		}
		return V128.build32(i -> values[i]);
	}


	void storeI8(int address, byte value) throws Throwable;

	void storeI16(int address, short value) throws Throwable;

	void storeI32(int address, int value) throws Throwable;

	void storeI64(int address, long value) throws Throwable;

	void storeF32(int address, float value) throws Throwable;

	void storeF64(int address, double value) throws Throwable;

	default void storeV128(int address, V128 value) throws Throwable {
		for(int i = 0; i < 4; ++i) {
			storeI32(address + i * 4, value.extractLane32(i));
		}
	}

	default void init(int d, int s, int n, Data data) throws Throwable {
		if(!Util.sumInRange(s, n, data.init().length) || !Util.sumInRange(d, n, byteSize())) {
			throw new IndexOutOfBoundsException();
		}

		while(n != 0) {
			storeI8(d, data.init()[s]);
			++d;
			++s;
			--n;
		}
	}
}
