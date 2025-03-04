package dev.argon.jawawasm.engine;

import dev.argon.jawawasm.format.data.V128;
import dev.argon.jawawasm.format.modules.Data;
import dev.argon.jawawasm.format.types.MemType;

/**
 * A non-resizable memory.
 */
public interface WasmMemoryNoResize {
	MemType.AddrType addressType();

	/**
	 * Gets the size of the memory in bytes.
	 * @return The size of the memory in bytes.
	 */
	long byteSize();

	/**
	 * Gets the size of the memory in pages.
	 * @return The size of the memory in pages.
	 */
	long pageSize();

	/**
	 * Reads an 8-bit value.
	 * @param address The address to read.
	 * @return The value.
	 */
	byte loadI8(long address);

	/**
	 * Reads a 16-bit value.
	 * @param address The address to read.
	 * @return The value.
	 */
	short loadI16(long address);

	/**
	 * Reads a 32-bit value.
	 * @param address The address to read.
	 * @return The value.
	 */
	int loadI32(long address);

	/**
	 * Reads a 64-bit value.
	 * @param address The address to read.
	 * @return The value.
	 */
	long loadI64(long address);

	/**
	 * Reads a 32-bit float value.
	 * @param address The address to read.
	 * @return The value.
	 */
	float loadF32(long address);

	/**
	 * Reads a 64-bit float value.
	 * @param address The address to read.
	 * @return The value.
	 */
	double loadF64(long address);


	/**
	 * Reads a V128 value.
	 * @param address The address to read.
	 * @return The value.
	 */
	default V128 loadV128(long address) {
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
	void storeI8(long address, byte value);

	/**
	 * Stores a 16-bit value.
	 * @param address The address to read.
	 * @param value The value.
	 */
	void storeI16(long address, short value);

	/**
	 * Stores an 32-bit value.
	 * @param address The address to read.
	 * @param value The value.
	 */
	void storeI32(long address, int value);

	/**
	 * Stores a 64-bit value.
	 * @param address The address to read.
	 * @param value The value.
	 */
	void storeI64(long address, long value);

	/**
	 * Stores a 32-bit float value.
	 * @param address The address to read.
	 * @param value The value.
	 */
	void storeF32(long address, float value);

	/**
	 * Stores a 64-bit float value.
	 * @param address The address to read.
	 * @param value The value.
	 */
	void storeF64(long address, double value);

	/**
	 * Stores a V128 value.
	 * @param address The address to read.
	 * @param value The value.
	 */
	default void storeV128(long address, V128 value) {
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
	default void init(long d, int s, int n, Data data) {
		copyFromArray(d, s, n, data.init());
	}

	/**
	 * Copy data from an array.
	 * @param address The destination address in memory.
	 * @param offset The starting offset in the array.
	 * @param length The number of bytes to copy.
	 * @param data The data.
	 */
	default void copyFromArray(long address, int offset, int length, byte[] data) {
		if(!Util.sumInRange(offset, length, data.length) || !Util.sumInRange(address, length, byteSize())) {
			throw new IndexOutOfBoundsException();
		}

		while(length != 0) {
			storeI8(address, data[offset]);
			++address;
			++offset;
			--length;
		}
	}

	/**
	 * Copy data to an array.
	 * @param address The destination address in memory.
	 * @param offset The starting offset in the array.
	 * @param length The number of bytes to copy.
	 * @param data The data.
	 */
	default void copyToArray(long address, int offset, int length, byte[] data) {
		if(!Util.sumInRange(offset, length, data.length) || !Util.sumInRange(address, length, byteSize())) {
			throw new IndexOutOfBoundsException();
		}

		while(length != 0) {
			data[offset] = loadI8(address);
			++address;
			++offset;
			--length;
		}
	}

}
