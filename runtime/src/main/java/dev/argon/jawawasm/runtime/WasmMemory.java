package dev.argon.jawawasm.runtime;

import org.jspecify.annotations.Nullable;

/**
 * A WebAssembly memory space.
 */
public sealed abstract class WasmMemory extends WasmMemoryNoResize permits WasmMemoryMeta {

	/**
	 * Gets the maximum size of the memory in pages.
	 * @return The maximum size of the memory or null if no maximum.
	 */
	public abstract @Nullable Long maxPageSize();

	/**
	 * Grow the memory.
	 * @param pages The number of pages by which to grow the memory.
	 * @return The old number of pages or -1 if it could not be grown.
	 */
	public abstract long grow(long pages);

	/**
	 * Ensures that the memory has a minimum size.
	 * @param pages The minimum number of pages.
	 */
	public final void ensureMinimumSize(int pages) {
		if(pageSize() < pages) {
			throw new ModuleLinkException("incompatible import type: Memory size is too small");
		}
	}

	/**
	 * Creates a WasmMemory
	 * @param allocator The allocator that will allocate memory.
	 * @param addrType The address type.
	 * @param minSize The minimum size of the memory.
	 * @param maxSize The maximum size of the memory.
	 * @return The created memory.
	 */
	public static WasmMemory create(MemoryAllocator allocator, AddrType addrType, long minSize, @Nullable Long maxSize) {
		return new WasmMemoryMeta(allocator, maxSize, allocator.allocateMemory(addrType, minSize));
	}

	/**
	 * Grow the memory.
	 * @param pages The number of pages by which to grow the memory.
	 * @param mem The memory to grow.
	 * @return The old number of pages or -1 if it could not be grown.
	 */
	public static long grow(long pages, WasmMemory mem) {
		return mem.grow(pages);
	}

	/**
	 * Grow the memory.
	 * @param pages The number of pages by which to grow the memory.
	 * @param mem The memory to grow.
	 * @return The old number of pages or -1 if it could not be grown.
	 */
	public static int grow(int pages, WasmMemory mem) {
		return (int)mem.grow(Integer.toUnsignedLong(pages));
	}

	/**
	 * Fills a range of memory with a byte value.
	 * @param d The starting address.
	 * @param val The value to fill.
	 * @param n The number of bytes to fill.
	 * @param memory The memory to fill.
	 */
	public static void fill(long d, byte val, long n, WasmMemory memory) {
		memory.fill(d, val, n);
	}

	/**
	 * Fills a range of memory with a byte value.
	 * @param d The starting address.
	 * @param val The value to fill.
	 * @param n The number of bytes to fill.
	 * @param memory The memory to fill.
	 */
	public static void fill(int d, byte val, int n, WasmMemory memory) {
		memory.fill(Integer.toUnsignedLong(d), val, Integer.toUnsignedLong(n));
	}

	/**
	 * Copies data within a memory.
	 * @param d The start address of the destination.
	 * @param s The start address of the source.
	 * @param n The number of bytes to copy.
	 * @param dstMemory The destination memory.
	 * @param srcMemory The source memory.
	 */
	public static void copy(long d, long s, long n, WasmMemory dstMemory, WasmMemory srcMemory) {
		dstMemory.copyFrom(d, s, n, srcMemory);
	}

	/**
	 * Copies data within a memory.
	 * @param d The start address of the destination.
	 * @param s The start address of the source.
	 * @param n The number of bytes to copy.
	 * @param dstMemory The destination memory.
	 * @param srcMemory The source memory.
	 */
	public static void copy(int d, int s, int n, WasmMemory dstMemory, WasmMemory srcMemory) {
		dstMemory.copyFrom(Integer.toUnsignedLong(d), Integer.toUnsignedLong(s), Integer.toUnsignedLong(n), srcMemory);
	}


	/**
	 * Copy data from an array.
	 * @param address The destination address in memory.
	 * @param offset The starting offset in the array.
	 * @param length The number of bytes to copy.
	 * @param data The data.
	 * @param memory The destination memory.
	 */
	public static void copyFromArray(long address, int offset, int length, byte[] data, WasmMemory memory) {
		memory.copyFromArray(address, offset, length, data);
	}

	/**
	 * Copy data from an array.
	 * @param address The destination address in memory.
	 * @param offset The starting offset in the array.
	 * @param length The number of bytes to copy.
	 * @param data The data.
	 * @param memory The destination memory.
	 */
	public static void copyFromArray(int address, int offset, int length, byte[] data, WasmMemory memory) {
		memory.copyFromArray(Integer.toUnsignedLong(address), offset, length, data);
	}

	/**
	 * Loads an 8-bit integer from memory at the specified address with offset.
	 *
	 * @param address the base memory address as an int
	 * @param offset the offset from the base address as an int
	 * @param memory the WasmMemory instance to load from
	 * @return the 8-bit integer value at the specified memory location
	 */
	public static byte loadI8(int address, int offset, WasmMemory memory) {
		return memory.loadI8(Integer.toUnsignedLong(address) + Integer.toUnsignedLong(offset));
	}

	/**
	 * Loads an 8-bit integer from memory at the specified address with offset.
	 *
	 * @param address the base memory address as a long
	 * @param offset the offset from the base address as a long
	 * @param memory the WasmMemory instance to load from
	 * @return the 8-bit integer value at the specified memory location
	 */
	public static byte loadI8(long address, long offset, WasmMemory memory) {
		if (address < 0 || offset < 0) {
			throw new IndexOutOfBoundsException();
		}
		return memory.loadI8(address + offset);
	}

	/**
	 * Stores an 8-bit integer to memory at the specified address with offset.
	 *
	 * @param address the base memory address as an int
	 * @param value the 8-bit integer value to store
	 * @param offset the offset from the base address as an int
	 * @param memory the WasmMemory instance to store to
	 */
	public static void storeI8(int address, byte value, int offset, WasmMemory memory) {
		memory.storeI8(Integer.toUnsignedLong(address) + Integer.toUnsignedLong(offset), value);
	}

	/**
	 * Stores an 8-bit integer to memory at the specified address with offset.
	 *
	 * @param address the base memory address as a long
	 * @param value the 8-bit integer value to store
	 * @param offset the offset from the base address as a long
	 * @param memory the WasmMemory instance to store to
	 */
	public static void storeI8(long address, byte value, long offset, WasmMemory memory) {
		if (address < 0 || offset < 0) {
			throw new IndexOutOfBoundsException();
		}
		memory.storeI8(address + offset, value);
	}

	/**
	 * Loads a 16-bit integer from memory at the specified address with offset.
	 *
	 * @param address the base memory address as an int
	 * @param offset the offset from the base address as an int
	 * @param memory the WasmMemory instance to load from
	 * @return the 16-bit integer value at the specified memory location
	 */
	public static short loadI16(int address, int offset, WasmMemory memory) {
		return memory.loadI16(Integer.toUnsignedLong(address) + Integer.toUnsignedLong(offset));
	}

	/**
	 * Loads a 16-bit integer from memory at the specified address with offset.
	 *
	 * @param address the base memory address as a long
	 * @param offset the offset from the base address as a long
	 * @param memory the WasmMemory instance to load from
	 * @return the 16-bit integer value at the specified memory location
	 */
	public static short loadI16(long address, long offset, WasmMemory memory) {
		if (address < 0 || offset < 0) {
			throw new IndexOutOfBoundsException();
		}
		return memory.loadI16(address + offset);
	}

	/**
	 * Stores a 16-bit integer to memory at the specified address with offset.
	 *
	 * @param address the base memory address as an int
	 * @param value the 16-bit integer value to store
	 * @param offset the offset from the base address as an int
	 * @param memory the WasmMemory instance to store to
	 */
	public static void storeI16(int address, short value, int offset, WasmMemory memory) {
		memory.storeI16(Integer.toUnsignedLong(address) + Integer.toUnsignedLong(offset), value);
	}

	/**
	 * Stores a 16-bit integer to memory at the specified address with offset.
	 *
	 * @param address the base memory address as a long
	 * @param value the 16-bit integer value to store
	 * @param offset the offset from the base address as a long
	 * @param memory the WasmMemory instance to store to
	 */
	public static void storeI16(long address, short value, long offset, WasmMemory memory) {
		if (address < 0 || offset < 0) {
			throw new IndexOutOfBoundsException();
		}
		memory.storeI16(address + offset, value);
	}

	/**
	 * Loads a 32-bit integer from memory at the specified address with offset.
	 *
	 * @param address the base memory address as an int
	 * @param offset the offset from the base address as an int
	 * @param memory the WasmMemory instance to load from
	 * @return the 32-bit integer value at the specified memory location
	 */
	public static int loadI32(int address, int offset, WasmMemory memory) {
		return memory.loadI32(Integer.toUnsignedLong(address) + Integer.toUnsignedLong(offset));
	}

	/**
	 * Loads a 32-bit integer from memory at the specified address with offset.
	 *
	 * @param address the base memory address as a long
	 * @param offset the offset from the base address as a long
	 * @param memory the WasmMemory instance to load from
	 * @return the 32-bit integer value at the specified memory location
	 */
	public static int loadI32(long address, long offset, WasmMemory memory) {
		if (address < 0 || offset < 0) {
			throw new IndexOutOfBoundsException();
		}
		return memory.loadI32(address + offset);
	}

	/**
	 * Stores a 32-bit integer to memory at the specified address with offset.
	 *
	 * @param address the base memory address as an int
	 * @param value the 32-bit integer value to store
	 * @param offset the offset from the base address as an int
	 * @param memory the WasmMemory instance to store to
	 */
	public static void storeI32(int address, int value, int offset, WasmMemory memory) {
		memory.storeI32(Integer.toUnsignedLong(address) + Integer.toUnsignedLong(offset), value);
	}

	/**
	 * Stores a 32-bit integer to memory at the specified address with offset.
	 *
	 * @param address the base memory address as a long
	 * @param value the 32-bit integer value to store
	 * @param offset the offset from the base address as a long
	 * @param memory the WasmMemory instance to store to
	 */
	public static void storeI32(long address, int value, long offset, WasmMemory memory) {
		if (address < 0 || offset < 0) {
			throw new IndexOutOfBoundsException();
		}
		memory.storeI32(address + offset, value);
	}

	/**
	 * Loads a 64-bit integer from memory at the specified address with offset.
	 *
	 * @param address the base memory address as an int
	 * @param offset the offset from the base address as an int
	 * @param memory the WasmMemory instance to load from
	 * @return the 64-bit integer value at the specified memory location
	 */
	public static long loadI64(int address, int offset, WasmMemory memory) {
		return memory.loadI64(Integer.toUnsignedLong(address) + Integer.toUnsignedLong(offset));
	}

	/**
	 * Loads a 64-bit integer from memory at the specified address with offset.
	 *
	 * @param address the base memory address as a long
	 * @param offset the offset from the base address as a long
	 * @param memory the WasmMemory instance to load from
	 * @return the 64-bit integer value at the specified memory location
	 */
	public static long loadI64(long address, long offset, WasmMemory memory) {
		if (address < 0 || offset < 0) {
			throw new IndexOutOfBoundsException();
		}
		return memory.loadI64(address + offset);
	}

	/**
	 * Stores a 64-bit integer to memory at the specified address with offset.
	 *
	 * @param address the base memory address as an int
	 * @param value the 64-bit integer value to store
	 * @param offset the offset from the base address as an int
	 * @param memory the WasmMemory instance to store to
	 */
	public static void storeI64(int address, long value, int offset, WasmMemory memory) {
		memory.storeI64(Integer.toUnsignedLong(address) + Integer.toUnsignedLong(offset), value);
	}

	/**
	 * Stores a 64-bit integer to memory at the specified address with offset.
	 *
	 * @param address the base memory address as a long
	 * @param value the 64-bit integer value to store
	 * @param offset the offset from the base address as a long
	 * @param memory the WasmMemory instance to store to
	 */
	public static void storeI64(long address, long value, long offset, WasmMemory memory) {
		if (address < 0 || offset < 0) {
			throw new IndexOutOfBoundsException();
		}
		memory.storeI64(address + offset, value);
	}

	/**
	 * Loads a 32-bit float from memory at the specified address with offset.
	 *
	 * @param address the base memory address as an int
	 * @param offset the offset from the base address as an int
	 * @param memory the WasmMemory instance to load from
	 * @return the 32-bit float value at the specified memory location
	 */
	public static float loadF32(int address, int offset, WasmMemory memory) {
		return memory.loadF32(Integer.toUnsignedLong(address) + Integer.toUnsignedLong(offset));
	}

	/**
	 * Loads a 32-bit float from memory at the specified address with offset.
	 *
	 * @param address the base memory address as a long
	 * @param offset the offset from the base address as a long
	 * @param memory the WasmMemory instance to load from
	 * @return the 32-bit float value at the specified memory location
	 */
	public static float loadF32(long address, long offset, WasmMemory memory) {
		if (address < 0 || offset < 0) {
			throw new IndexOutOfBoundsException();
		}
		return memory.loadF32(address + offset);
	}

	/**
	 * Stores a 32-bit float to memory at the specified address with offset.
	 *
	 * @param address the base memory address as an int
	 * @param value the 32-bit float value to store
	 * @param offset the offset from the base address as an int
	 * @param memory the WasmMemory instance to store to
	 */
	public static void storeF32(int address, float value, int offset, WasmMemory memory) {
		memory.storeF32(Integer.toUnsignedLong(address) + Integer.toUnsignedLong(offset), value);
	}

	/**
	 * Stores a 32-bit float to memory at the specified address with offset.
	 *
	 * @param address the base memory address as a long
	 * @param value the 32-bit float value to store
	 * @param offset the offset from the base address as a long
	 * @param memory the WasmMemory instance to store to
	 */
	public static void storeF32(long address, float value, long offset, WasmMemory memory) {
		if (address < 0 || offset < 0) {
			throw new IndexOutOfBoundsException();
		}
		memory.storeF32(address + offset, value);
	}

	/**
	 * Loads a 64-bit double from memory at the specified address with offset.
	 *
	 * @param address the base memory address as an int
	 * @param offset the offset from the base address as an int
	 * @param memory the WasmMemory instance to load from
	 * @return the 64-bit double value at the specified memory location
	 */
	public static double loadF64(int address, int offset, WasmMemory memory) {
		return memory.loadF64(Integer.toUnsignedLong(address) + Integer.toUnsignedLong(offset));
	}

	/**
	 * Loads a 64-bit double from memory at the specified address with offset.
	 *
	 * @param address the base memory address as a long
	 * @param offset the offset from the base address as a long
	 * @param memory the WasmMemory instance to load from
	 * @return the 64-bit double value at the specified memory location
	 */
	public static double loadF64(long address, long offset, WasmMemory memory) {
		if (address < 0 || offset < 0) {
			throw new IndexOutOfBoundsException();
		}
		return memory.loadF64(address + offset);
	}

	/**
	 * Stores a 64-bit double to memory at the specified address with offset.
	 *
	 * @param address the base memory address as an int
	 * @param value the 64-bit double value to store
	 * @param offset the offset from the base address as an int
	 * @param memory the WasmMemory instance to store to
	 */
	public static void storeF64(int address, double value, int offset, WasmMemory memory) {
		memory.storeF64(Integer.toUnsignedLong(address) + Integer.toUnsignedLong(offset), value);
	}

	/**
	 * Stores a 64-bit double to memory at the specified address with offset.
	 *
	 * @param address the base memory address as a long
	 * @param value the 64-bit double value to store
	 * @param offset the offset from the base address as a long
	 * @param memory the WasmMemory instance to store to
	 */
	public static void storeF64(long address, double value, long offset, WasmMemory memory) {
		if (address < 0 || offset < 0) {
			throw new IndexOutOfBoundsException();
		}
		memory.storeF64(address + offset, value);
	}

	/**
	 * Loads a 128-bit vector from memory at the specified address with offset.
	 *
	 * @param address the base memory address as an int
	 * @param offset the offset from the base address as an int
	 * @param memory the WasmMemory instance to load from
	 * @return a V128 instance containing the 128-bit vector value
	 */
	public static V128 loadV128(int address, int offset, WasmMemory memory) {
		return memory.loadV128(Integer.toUnsignedLong(address) + Integer.toUnsignedLong(offset));
	}

	/**
	 * Loads a 128-bit vector from memory at the specified address with offset.
	 *
	 * @param address the base memory address as a long
	 * @param offset the offset from the base address as a long
	 * @param memory the WasmMemory instance to load from
	 * @return a V128 instance containing the 128-bit vector value
	 */
	public static V128 loadV128(long address, long offset, WasmMemory memory) {
		if (address < 0 || offset < 0) {
			throw new IndexOutOfBoundsException();
		}
		return memory.loadV128(address + offset);
	}

	/**
	 * Stores a 128-bit vector to memory at the specified address with offset.
	 *
	 * @param address the base memory address as an int
	 * @param value the V128 instance containing the 128-bit vector value to store
	 * @param offset the offset from the base address as an int
	 * @param memory the WasmMemory instance to store to
	 */
	public static void storeV128(int address, V128 value, int offset, WasmMemory memory) {
		memory.storeV128(Integer.toUnsignedLong(address) + Integer.toUnsignedLong(offset), value);
	}

	/**
	 * Stores a 128-bit vector to memory at the specified address with offset.
	 *
	 * @param address the base memory address as a long
	 * @param value the V128 instance containing the 128-bit vector value to store
	 * @param offset the offset from the base address as a long
	 * @param memory the WasmMemory instance to store to
	 */
	public static void storeV128(long address, V128 value, long offset, WasmMemory memory) {
		if (address < 0 || offset < 0) {
			throw new IndexOutOfBoundsException();
		}
		memory.storeV128(address + offset, value);
	}
}
