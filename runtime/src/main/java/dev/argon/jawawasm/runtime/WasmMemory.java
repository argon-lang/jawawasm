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
	 * @return The old number of pages.
	 */
	public abstract long grow(long pages);

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
	 * Copies data within a memory.
	 * @param d The start address of the destination.
	 * @param s The start address of the source.
	 * @param n The number of bytes to copy.
	 * @param memory The memory to copy data within.
	 */
	public static void copy(long d, long s, long n, WasmMemory memory) {
		if(!Util.sumInRange(d, n, memory.byteSize()) || !Util.sumInRange(s, n, memory.byteSize())) {
			throw new IndexOutOfBoundsException();
		}

		if(d <= s) {
			while(n != 0) {
				byte b = memory.loadI8(s);
				memory.storeI8(d, b);
				++d;
				++s;
				--n;
			}
		}
		else {
			while(n != 0) {
				byte b = memory.loadI8(s + n - 1);
				memory.storeI8(d + n - 1, b);
				--n;
			}
		}
	}
}
