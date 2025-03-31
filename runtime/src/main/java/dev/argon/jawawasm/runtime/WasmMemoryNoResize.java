package dev.argon.jawawasm.runtime;

/**
 * A non-resizable memory.
 */
public abstract class WasmMemoryNoResize {
	/**
	 * Create a memory.
	 */
	public WasmMemoryNoResize() {}

	/**
	 * Gets the address type.
	 * @return The address type.
	 */
	public abstract AddrType addressType();

	/**
	 * Gets the size of the memory in bytes.
	 * @return The size of the memory in bytes.
	 */
	public abstract long byteSize();

	/**
	 * Gets the size of the memory in pages.
	 * @return The size of the memory in pages.
	 */
	public abstract long pageSize();

	/**
	 * Reads an 8-bit value.
	 * @param address The address to read.
	 * @return The value.
	 */
	public abstract byte loadI8(long address);

	/**
	 * Reads a 16-bit value.
	 * @param address The address to read.
	 * @return The value.
	 */
	public abstract short loadI16(long address);

	/**
	 * Reads a 32-bit value.
	 * @param address The address to read.
	 * @return The value.
	 */
	public abstract int loadI32(long address);

	/**
	 * Reads a 64-bit value.
	 * @param address The address to read.
	 * @return The value.
	 */
	public abstract long loadI64(long address);

	/**
	 * Reads a 32-bit float value.
	 * @param address The address to read.
	 * @return The value.
	 */
	public abstract float loadF32(long address);

	/**
	 * Reads a 64-bit float value.
	 * @param address The address to read.
	 * @return The value.
	 */
	public abstract double loadF64(long address);


	/**
	 * Reads a V128 value.
	 * @param address The address to read.
	 * @return The value.
	 */
	public V128 loadV128(long address) {
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
	public abstract void storeI8(long address, byte value);

	/**
	 * Stores a 16-bit value.
	 * @param address The address to read.
	 * @param value The value.
	 */
	public abstract void storeI16(long address, short value);

	/**
	 * Stores an 32-bit value.
	 * @param address The address to read.
	 * @param value The value.
	 */
	public abstract void storeI32(long address, int value);

	/**
	 * Stores a 64-bit value.
	 * @param address The address to read.
	 * @param value The value.
	 */
	public abstract void storeI64(long address, long value);

	/**
	 * Stores a 32-bit float value.
	 * @param address The address to read.
	 * @param value The value.
	 */
	public abstract void storeF32(long address, float value);

	/**
	 * Stores a 64-bit float value.
	 * @param address The address to read.
	 * @param value The value.
	 */
	public abstract void storeF64(long address, double value);

	/**
	 * Stores a V128 value.
	 * @param address The address to read.
	 * @param value The value.
	 */
	public void storeV128(long address, V128 value) {
		storeI32(address, value.extractLane32(0));
		storeI32(address + 4, value.extractLane32(1));
		storeI32(address + 8, value.extractLane32(2));
		storeI32(address + 12, value.extractLane32(3));
	}

	/**
	 * Fills a range of memory with a byte value.
	 * @param d The starting address.
	 * @param val The value to fill.
	 * @param n The number of bytes to fill.
	 */
	public void fill(long d, byte val, long n) {
		if(!Util.sumInRange(d, n, byteSize())) {
			throw new IndexOutOfBoundsException();
		}

		while(n != 0) {
			storeI8(d, val);
			++d;
			--n;
		}
	}

	/**
	 * Copies data between memories.
	 * @param d The start address of the destination.
	 * @param s The start address of the source.
	 * @param n The number of bytes to copy.
	 * @param srcMemory The source memory.
	 */
	public void copyFrom(long d, long s, long n, WasmMemory srcMemory) {
		if(!Util.sumInRange(d, n, byteSize()) || !Util.sumInRange(s, n, byteSize())) {
			throw new IndexOutOfBoundsException();
		}

		if(d <= s) {
			while(n != 0) {
				byte b = srcMemory.loadI8(s);
				storeI8(d, b);
				++d;
				++s;
				--n;
			}
		}
		else {
			while(n != 0) {
				byte b = srcMemory.loadI8(s + n - 1);
				storeI8(d + n - 1, b);
				--n;
			}
		}
	}

	/**
	 * Copy data from another memory.
	 * @param other The other memory.
	 */
	public void copyFrom(WasmMemoryNoResize other) {
		long pagesToCopy = other.pageSize();

		if(pagesToCopy > pageSize()) {
			throw new IndexOutOfBoundsException();
		}

		long byteSize = pagesToCopy * Util.PAGE_SIZE;

		for(long address = 0; address < byteSize; address += 8) {
			storeI64(address, other.loadI64(address));
		}
	}


	/**
	 * Copy data from an array.
	 * @param address The destination address in memory.
	 * @param offset The starting offset in the array.
	 * @param length The number of bytes to copy.
	 * @param data The data.
	 */
	public void copyFromArray(long address, int offset, int length, byte[] data) {
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
	public void copyToArray(long address, int offset, int length, byte[] data) {
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
