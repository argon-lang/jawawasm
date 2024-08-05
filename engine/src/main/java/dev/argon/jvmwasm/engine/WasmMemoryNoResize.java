package dev.argon.jvmwasm.engine;

import dev.argon.jvmwasm.format.data.V128;
import dev.argon.jvmwasm.format.modules.Data;

/**
 * A non-resizable memory.
 */
public interface WasmMemoryNoResize {
	/**
	 * Gets the size of the memory in bytes.
	 * @return The size of the memory in bytes.
	 */
	int byteSize();

	/**
	 * Gets the size of the memory in pages.
	 * @return The size of the memory in pages.
	 */
	int pageSize();

	/**
	 * Reads an 8-bit value.
	 * @param address The address to read.
	 * @return The value.
	 */
	byte loadI8(int address);

	/**
	 * Reads a 16-bit value.
	 * @param address The address to read.
	 * @return The value.
	 */
	short loadI16(int address);

	/**
	 * Reads a 32-bit value.
	 * @param address The address to read.
	 * @return The value.
	 */
	int loadI32(int address);

	/**
	 * Reads a 64-bit value.
	 * @param address The address to read.
	 * @return The value.
	 */
	long loadI64(int address);

	/**
	 * Reads a 32-bit float value.
	 * @param address The address to read.
	 * @return The value.
	 */
	float loadF32(int address);

	/**
	 * Reads a 64-bit float value.
	 * @param address The address to read.
	 * @return The value.
	 */
	double loadF64(int address);


	/**
	 * Reads a V128 value.
	 * @param address The address to read.
	 * @return The value.
	 */
	default V128 loadV128(int address) {
		int[] values = new int[4];
		for(int i = 0; i < values.length; ++i) {
			values[i] = loadI32(address + i * 4);
		}
		return V128.build32(i -> values[i]);
	}


	/**
	 * Stores an 8-bit value.
	 * @param address The address to read.
	 * @param value The value.
	 */
	void storeI8(int address, byte value);

	/**
	 * Stores a 16-bit value.
	 * @param address The address to read.
	 * @param value The value.
	 */
	void storeI16(int address, short value);

	/**
	 * Stores an 32-bit value.
	 * @param address The address to read.
	 * @param value The value.
	 */
	void storeI32(int address, int value);

	/**
	 * Stores a 64-bit value.
	 * @param address The address to read.
	 * @param value The value.
	 */
	void storeI64(int address, long value);

	/**
	 * Stores a 32-bit float value.
	 * @param address The address to read.
	 * @param value The value.
	 */
	void storeF32(int address, float value);

	/**
	 * Stores a 64-bit float value.
	 * @param address The address to read.
	 * @param value The value.
	 */
	void storeF64(int address, double value);

	/**
	 * Stores a V128 value.
	 * @param address The address to read.
	 * @param value The value.
	 */
	default void storeV128(int address, V128 value) {
		for(int i = 0; i < 4; ++i) {
			storeI32(address + i * 4, value.extractLane32(i));
		}
	}

	/**
	 * Initialize the memory.
	 * @param d The destination address.
	 * @param s The source address.
	 * @param n The number of types to copy.
	 * @param data The data source.
	 */
	default void init(int d, int s, int n, Data data) {
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
