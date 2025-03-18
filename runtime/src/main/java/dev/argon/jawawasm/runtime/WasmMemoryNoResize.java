package dev.argon.jawawasm.runtime;

/**
 * A non-resizable memory.
 */
public interface WasmMemoryNoResize {
	/**
	 * Gets the address type.
	 * @return The address type.
	 */
	AddrType addressType();

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
		values[0] = loadI32(address);
		values[1] = loadI32(address + 4);
		values[2] = loadI32(address + 8);
		values[3] = loadI32(address + 12);
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
		storeI32(address, value.extractLane32(0));
		storeI32(address + 4, value.extractLane32(1));
		storeI32(address + 8, value.extractLane32(2));
		storeI32(address + 12, value.extractLane32(3));
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

	default void copyFrom(WasmMemoryNoResize other) {
		long pagesToCopy = other.pageSize();

		if(pagesToCopy > pageSize()) {
			throw new IndexOutOfBoundsException();
		}

		long byteSize = pagesToCopy * Util.PAGE_SIZE;

		for(long address = 0; address < byteSize; address += 8) {
			storeI64(address, other.loadI64(address));
		}
	}

}
