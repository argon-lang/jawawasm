package dev.argon.jawawasm.runtime;

import org.jspecify.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * A WebAssembly memory with unsigned 32-bit addresses. Addresses, offsets, and
 * memory range lengths use unsigned int values. Array offsets and lengths use
 * nonnegative int values. Loads and stores use little-endian byte order and
 * allow unaligned access. Static helpers place operands before the memory to
 * match the bytecode stack.
 */
public final class WasmMemory32 implements WasmMemory32Base {
	WasmMemory32(RuntimeOptions runtimeOptions, int pages, @Nullable Integer maxPages) {
		if(pages < 0 || pages > MemoryConstants.MAX_PAGES_32
			|| (maxPages != null && (maxPages < pages || maxPages > MemoryConstants.MAX_PAGES_32))) {
			throw new IllegalArgumentException("invalid memory size");
		}
		long limit = runtimeOptions.maxMemory();
		if(limit > 0 && pages > limit) {
			throw new IllegalArgumentException("memory size exceeds runtime limit");
		}
		this.runtimeOptions = runtimeOptions;
		this.pages = pages;
		this.maxPages = maxPages;
		segment = allocate(pages);
	}

	private final RuntimeOptions runtimeOptions;
	private int pages;
	private final @Nullable Integer maxPages;
	private MemorySegment segment;

	MemorySegment segment() {
		return segment;
	}

	private static MemorySegment allocate(int pages) {
		return Arena.ofAuto().allocate((long)pages * MemoryConstants.PAGE_SIZE);
	}

	/**
	 * Creates a memory with unsigned 32-bit addresses.
	 * 
	 * @param runtimeOptions
	 *            The runtime limits.
	 * @param minSize
	 *            The initial size in pages.
	 * @param maxSize
	 *            The maximum size in pages, or null if no declared maximum.
	 * @return The created memory.
	 * @throws IllegalArgumentException
	 *             If the sizes are invalid or exceed the runtime limit.
	 */
	public static WasmMemory32 create(RuntimeOptions runtimeOptions, int minSize, @Nullable Integer maxSize) {
		return new WasmMemory32(runtimeOptions, minSize, maxSize);
	}

	/**
	 * Gets the address type.
	 * 
	 * @return The address type.
	 */
	public AddrType addressType() {
		return AddrType.I32;
	}

	/**
	 * Gets the size in bytes, using a long to represent a full 4 GiB memory.
	 * 
	 * @return The size in bytes.
	 */
	public long byteSize() {
		return segment.byteSize();
	}

	/**
	 * Gets the size of the memory in pages.
	 * 
	 * @return The size of the memory in pages.
	 */
	public int pageSize() {
		return pages;
	}

	/**
	 * Gets the maximum size of the memory in pages.
	 * 
	 * @return The maximum size of the memory or null if no maximum.
	 */
	public @Nullable Integer maxPageSize() {
		return maxPages;
	}

	/**
	 * Ensures that the memory has a minimum size.
	 * 
	 * @param pages
	 *            The minimum number of pages.
	 */
	public void ensureMinimumSize(int pages) {
		if(Integer.compareUnsigned(this.pages, pages) < 0) {
			throw new ModuleLinkException("incompatible import type: Memory size is too small");
		}
	}

	/**
	 * Grows the memory.
	 * 
	 * @param pages
	 *            The unsigned number of pages to add.
	 * @return The previous page count, or -1 if growth fails.
	 */
	public int grow(int pages) {
		long newPages = this.pages + Integer.toUnsignedLong(pages);
		long limit = runtimeOptions.maxMemory();
		if(newPages > MemoryConstants.MAX_PAGES_32 || (maxPages != null && newPages > maxPages)
			|| (limit > 0 && newPages > limit)) {
			return -1;
		}
		int oldPages = this.pages;
		if(pages == 0) {
			return oldPages;
		}
		MemorySegment newSegment;
		try {
			newSegment = allocate((int)newPages);
		}
		catch(OutOfMemoryError ex) {
			return -1;
		}
		newSegment.copyFrom(segment);
		segment = newSegment;
		this.pages = (int)newPages;
		return oldPages;
	}

	/**
	 * Reads an 8-bit value.
	 * 
	 * @param address
	 *            The address to read.
	 * @return The value.
	 */
	public byte loadI8(int address) {
		return segment.get(MemoryConstants.I8_LAYOUT, Integer.toUnsignedLong(address));
	}

	/**
	 * Stores an 8-bit value.
	 * 
	 * @param address
	 *            The address to write.
	 * @param value
	 *            The value.
	 */
	public void storeI8(int address, byte value) {
		segment.set(MemoryConstants.I8_LAYOUT, Integer.toUnsignedLong(address), value);
	}

	/**
	 * Reads a 16-bit value.
	 * 
	 * @param address
	 *            The address to read.
	 * @return The value.
	 */
	public short loadI16(int address) {
		return segment.get(MemoryConstants.I16_LAYOUT, Integer.toUnsignedLong(address));
	}

	/**
	 * Stores a 16-bit value.
	 * 
	 * @param address
	 *            The address to write.
	 * @param value
	 *            The value.
	 */
	public void storeI16(int address, short value) {
		segment.set(MemoryConstants.I16_LAYOUT, Integer.toUnsignedLong(address), value);
	}

	/**
	 * Reads a 32-bit value.
	 * 
	 * @param address
	 *            The address to read.
	 * @return The value.
	 */
	public int loadI32(int address) {
		return segment.get(MemoryConstants.I32_LAYOUT, Integer.toUnsignedLong(address));
	}

	/**
	 * Stores a 32-bit value.
	 * 
	 * @param address
	 *            The address to write.
	 * @param value
	 *            The value.
	 */
	public void storeI32(int address, int value) {
		segment.set(MemoryConstants.I32_LAYOUT, Integer.toUnsignedLong(address), value);
	}

	/**
	 * Reads a 64-bit value.
	 * 
	 * @param address
	 *            The address to read.
	 * @return The value.
	 */
	public long loadI64(int address) {
		return segment.get(MemoryConstants.I64_LAYOUT, Integer.toUnsignedLong(address));
	}

	/**
	 * Stores a 64-bit value.
	 * 
	 * @param address
	 *            The address to write.
	 * @param value
	 *            The value.
	 */
	public void storeI64(int address, long value) {
		segment.set(MemoryConstants.I64_LAYOUT, Integer.toUnsignedLong(address), value);
	}

	/**
	 * Reads a 32-bit float value.
	 * 
	 * @param address
	 *            The address to read.
	 * @return The value.
	 */
	public float loadF32(int address) {
		return segment.get(MemoryConstants.F32_LAYOUT, Integer.toUnsignedLong(address));
	}

	/**
	 * Stores a 32-bit float value.
	 * 
	 * @param address
	 *            The address to write.
	 * @param value
	 *            The value.
	 */
	public void storeF32(int address, float value) {
		segment.set(MemoryConstants.F32_LAYOUT, Integer.toUnsignedLong(address), value);
	}

	/**
	 * Reads a 64-bit float value.
	 * 
	 * @param address
	 *            The address to read.
	 * @return The value.
	 */
	public double loadF64(int address) {
		return segment.get(MemoryConstants.F64_LAYOUT, Integer.toUnsignedLong(address));
	}

	/**
	 * Stores a 64-bit float value.
	 * 
	 * @param address
	 *            The address to write.
	 * @param value
	 *            The value.
	 */
	public void storeF64(int address, double value) {
		segment.set(MemoryConstants.F64_LAYOUT, Integer.toUnsignedLong(address), value);
	}

	/**
	 * Reads a V128 value.
	 * 
	 * @param address
	 *            The address to read.
	 * @return The value.
	 */
	public V128 loadV128(int address) {
		var data = segment.asSlice(Integer.toUnsignedLong(address), 16);
		return new V128(data.get(MemoryConstants.I64_LAYOUT, 0), data.get(MemoryConstants.I64_LAYOUT, 8));
	}

	/**
	 * Stores a V128 value.
	 * 
	 * @param address
	 *            The address to write.
	 * @param value
	 *            The value.
	 */
	public void storeV128(int address, V128 value) {
		var data = segment.asSlice(Integer.toUnsignedLong(address), 16);
		data.set(MemoryConstants.I64_LAYOUT, 0, value.low());
		data.set(MemoryConstants.I64_LAYOUT, 8, value.high());
	}

	/**
	 * Fills a range of memory with a byte value.
	 * 
	 * @param d
	 *            The starting address.
	 * @param val
	 *            The value to fill.
	 * @param n
	 *            The number of bytes to fill.
	 */
	public void fill(int d, byte val, int n) {
		segment.asSlice(Integer.toUnsignedLong(d), Integer.toUnsignedLong(n)).fill(val);
	}

	/**
	 * Copies data between memories.
	 * 
	 * @param d
	 *            The start address of the destination.
	 * @param s
	 *            The start address of the source.
	 * @param n
	 *            The number of bytes to copy.
	 * @param srcMemory
	 *            The source memory.
	 */
	public void copyFrom(int d, int s, int n, WasmMemory32 srcMemory) {
		MemorySegment.copy(
			srcMemory.segment,
			Integer.toUnsignedLong(s),
			segment,
			Integer.toUnsignedLong(d),
			Integer.toUnsignedLong(n)
		);
	}

	/**
	 * Copy data from another memory.
	 * 
	 * @param other
	 *            The source memory; its contents must fit in this memory.
	 */
	public void copyFrom(WasmMemory32 other) {
		segment.copyFrom(other.segment);
	}

	/**
	 * Copy data from an array.
	 * 
	 * @param address
	 *            The destination address in memory.
	 * @param offset
	 *            The starting offset in the array.
	 * @param length
	 *            The number of bytes to copy.
	 * @param data
	 *            The data.
	 */
	public void copyFromArray(int address, int offset, int length, byte[] data) {
		MemorySegment.copy(MemorySegment.ofArray(data), offset, segment, Integer.toUnsignedLong(address), length);
	}

	/**
	 * Copy data to an array.
	 * 
	 * @param address
	 *            The source address in memory.
	 * @param offset
	 *            The starting offset in the array.
	 * @param length
	 *            The number of bytes to copy.
	 * @param data
	 *            The data.
	 */
	public void copyToArray(int address, int offset, int length, byte[] data) {
		MemorySegment.copy(segment, Integer.toUnsignedLong(address), MemorySegment.ofArray(data), offset, length);
	}

	private static int effectiveAddress(int address, int offset) {
		long result = Integer.toUnsignedLong(address) + Integer.toUnsignedLong(offset);
		if(result > 0xFFFF_FFFFL) {
			throw new IndexOutOfBoundsException();
		}
		return (int)result;
	}

	/**
	 * Grow the memory.
	 * 
	 * @param pages
	 *            The number of pages by which to grow the memory.
	 * @param mem
	 *            The memory to grow.
	 * @return The old number of pages or -1 if it could not be grown.
	 */
	public static int grow(int pages, WasmMemory32 mem) {
		return mem.grow(pages);
	}

	/**
	 * Fills a range of memory with a byte value.
	 * 
	 * @param d
	 *            The starting address.
	 * @param val
	 *            The value to fill.
	 * @param n
	 *            The number of bytes to fill.
	 * @param memory
	 *            The memory to fill.
	 */
	public static void fill(int d, byte val, int n, WasmMemory32 memory) {
		memory.fill(d, val, n);
	}

	/**
	 * Copies data within a memory.
	 * 
	 * @param d
	 *            The start address of the destination.
	 * @param s
	 *            The start address of the source.
	 * @param n
	 *            The number of bytes to copy.
	 * @param dstMemory
	 *            The destination memory.
	 * @param srcMemory
	 *            The source memory.
	 */
	public static void copy(int d, int s, int n, WasmMemory32 dstMemory, WasmMemory32 srcMemory) {
		dstMemory.copyFrom(d, s, n, srcMemory);
	}

	/**
	 * Copies data between memories with different address types.
	 * 
	 * @param d
	 *            The destination address.
	 * @param s
	 *            The source address.
	 * @param n
	 *            The unsigned number of bytes to copy.
	 * @param dstMemory
	 *            The destination memory.
	 * @param srcMemory
	 *            The source memory.
	 */
	public static void copy(int d, long s, int n, WasmMemory32 dstMemory, WasmMemory64 srcMemory) {
		MemorySegment
			.copy(srcMemory.segment(), s, dstMemory.segment, Integer.toUnsignedLong(d), Integer.toUnsignedLong(n));
	}

	/**
	 * Copy data from an array.
	 * 
	 * @param address
	 *            The destination address in memory.
	 * @param offset
	 *            The starting offset in the array.
	 * @param length
	 *            The number of bytes to copy.
	 * @param data
	 *            The data.
	 * @param memory
	 *            The destination memory.
	 */
	public static void copyFromArray(int address, int offset, int length, byte[] data, WasmMemory32 memory) {
		memory.copyFromArray(address, offset, length, data);
	}

	/**
	 * Loads an 8-bit integer from memory at the specified address.
	 *
	 * @param address
	 *            the memory address as an int
	 * @param memory
	 *            the WasmMemory32 instance to load from
	 * @return the 8-bit integer value at the specified memory location
	 */
	public static byte loadI8(int address, WasmMemory32 memory) {
		return memory.loadI8(address);
	}

	/**
	 * Loads an 8-bit integer from memory at the specified address with offset.
	 *
	 * @param address
	 *            the base memory address as an int
	 * @param offset
	 *            the offset from the base address as an int
	 * @param memory
	 *            the WasmMemory32 instance to load from
	 * @return the 8-bit integer value at the specified memory location
	 */
	public static byte loadI8(int address, int offset, WasmMemory32 memory) {
		return memory.loadI8(effectiveAddress(address, offset));
	}

	/**
	 * Stores an 8-bit integer to memory at the specified address.
	 *
	 * @param address
	 *            the memory address as an int
	 * @param value
	 *            the 8-bit integer value to store
	 * @param memory
	 *            the WasmMemory32 instance to store to
	 */
	public static void storeI8(int address, byte value, WasmMemory32 memory) {
		memory.storeI8(address, value);
	}

	/**
	 * Stores an 8-bit integer to memory at the specified address with offset.
	 *
	 * @param address
	 *            the base memory address as an int
	 * @param value
	 *            the 8-bit integer value to store
	 * @param offset
	 *            the offset from the base address as an int
	 * @param memory
	 *            the WasmMemory32 instance to store to
	 */
	public static void storeI8(int address, byte value, int offset, WasmMemory32 memory) {
		memory.storeI8(effectiveAddress(address, offset), value);
	}

	/**
	 * Loads a 16-bit integer from memory at the specified address.
	 *
	 * @param address
	 *            the memory address as an int
	 * @param memory
	 *            the WasmMemory32 instance to load from
	 * @return the 16-bit integer value at the specified memory location
	 */
	public static short loadI16(int address, WasmMemory32 memory) {
		return memory.loadI16(address);
	}

	/**
	 * Loads a 16-bit integer from memory at the specified address with offset.
	 *
	 * @param address
	 *            the base memory address as an int
	 * @param offset
	 *            the offset from the base address as an int
	 * @param memory
	 *            the WasmMemory32 instance to load from
	 * @return the 16-bit integer value at the specified memory location
	 */
	public static short loadI16(int address, int offset, WasmMemory32 memory) {
		return memory.loadI16(effectiveAddress(address, offset));
	}

	/**
	 * Stores a 16-bit integer to memory at the specified address.
	 *
	 * @param address
	 *            the memory address as an int
	 * @param value
	 *            the 16-bit integer value to store
	 * @param memory
	 *            the WasmMemory32 instance to store to
	 */
	public static void storeI16(int address, short value, WasmMemory32 memory) {
		memory.storeI16(address, value);
	}

	/**
	 * Stores a 16-bit integer to memory at the specified address with offset.
	 *
	 * @param address
	 *            the base memory address as an int
	 * @param value
	 *            the 16-bit integer value to store
	 * @param offset
	 *            the offset from the base address as an int
	 * @param memory
	 *            the WasmMemory32 instance to store to
	 */
	public static void storeI16(int address, short value, int offset, WasmMemory32 memory) {
		memory.storeI16(effectiveAddress(address, offset), value);
	}

	/**
	 * Loads a 32-bit integer from memory at the specified address.
	 *
	 * @param address
	 *            the memory address as an int
	 * @param memory
	 *            the WasmMemory32 instance to load from
	 * @return the 32-bit integer value at the specified memory location
	 */
	public static int loadI32(int address, WasmMemory32 memory) {
		return memory.loadI32(address);
	}

	/**
	 * Loads a 32-bit integer from memory at the specified address with offset.
	 *
	 * @param address
	 *            the base memory address as an int
	 * @param offset
	 *            the offset from the base address as an int
	 * @param memory
	 *            the WasmMemory32 instance to load from
	 * @return the 32-bit integer value at the specified memory location
	 */
	public static int loadI32(int address, int offset, WasmMemory32 memory) {
		return memory.loadI32(effectiveAddress(address, offset));
	}

	/**
	 * Stores a 32-bit integer to memory at the specified address.
	 *
	 * @param address
	 *            the memory address as an int
	 * @param value
	 *            the 32-bit integer value to store
	 * @param memory
	 *            the WasmMemory32 instance to store to
	 */
	public static void storeI32(int address, int value, WasmMemory32 memory) {
		memory.storeI32(address, value);
	}

	/**
	 * Stores a 32-bit integer to memory at the specified address with offset.
	 *
	 * @param address
	 *            the base memory address as an int
	 * @param value
	 *            the 32-bit integer value to store
	 * @param offset
	 *            the offset from the base address as an int
	 * @param memory
	 *            the WasmMemory32 instance to store to
	 */
	public static void storeI32(int address, int value, int offset, WasmMemory32 memory) {
		memory.storeI32(effectiveAddress(address, offset), value);
	}

	/**
	 * Loads a 64-bit integer from memory at the specified address.
	 *
	 * @param address
	 *            the memory address as an int
	 * @param memory
	 *            the WasmMemory32 instance to load from
	 * @return the 64-bit integer value at the specified memory location
	 */
	public static long loadI64(int address, WasmMemory32 memory) {
		return memory.loadI64(address);
	}

	/**
	 * Loads a 64-bit integer from memory at the specified address with offset.
	 *
	 * @param address
	 *            the base memory address as an int
	 * @param offset
	 *            the offset from the base address as an int
	 * @param memory
	 *            the WasmMemory32 instance to load from
	 * @return the 64-bit integer value at the specified memory location
	 */
	public static long loadI64(int address, int offset, WasmMemory32 memory) {
		return memory.loadI64(effectiveAddress(address, offset));
	}

	/**
	 * Stores a 64-bit integer to memory at the specified address.
	 *
	 * @param address
	 *            the memory address as an int
	 * @param value
	 *            the 64-bit integer value to store
	 * @param memory
	 *            the WasmMemory32 instance to store to
	 */
	public static void storeI64(int address, long value, WasmMemory32 memory) {
		memory.storeI64(address, value);
	}

	/**
	 * Stores a 64-bit integer to memory at the specified address with offset.
	 *
	 * @param address
	 *            the base memory address as an int
	 * @param value
	 *            the 64-bit integer value to store
	 * @param offset
	 *            the offset from the base address as an int
	 * @param memory
	 *            the WasmMemory32 instance to store to
	 */
	public static void storeI64(int address, long value, int offset, WasmMemory32 memory) {
		memory.storeI64(effectiveAddress(address, offset), value);
	}

	/**
	 * Loads a 32-bit float from memory at the specified address.
	 *
	 * @param address
	 *            the memory address as an int
	 * @param memory
	 *            the WasmMemory32 instance to load from
	 * @return the 32-bit float value at the specified memory location
	 */
	public static float loadF32(int address, WasmMemory32 memory) {
		return memory.loadF32(address);
	}

	/**
	 * Loads a 32-bit float from memory at the specified address with offset.
	 *
	 * @param address
	 *            the base memory address as an int
	 * @param offset
	 *            the offset from the base address as an int
	 * @param memory
	 *            the WasmMemory32 instance to load from
	 * @return the 32-bit float value at the specified memory location
	 */
	public static float loadF32(int address, int offset, WasmMemory32 memory) {
		return memory.loadF32(effectiveAddress(address, offset));
	}

	/**
	 * Stores a 32-bit float to memory at the specified address.
	 *
	 * @param address
	 *            the memory address as an int
	 * @param value
	 *            the 32-bit float value to store
	 * @param memory
	 *            the WasmMemory32 instance to store to
	 */
	public static void storeF32(int address, float value, WasmMemory32 memory) {
		memory.storeF32(address, value);
	}

	/**
	 * Stores a 32-bit float to memory at the specified address with offset.
	 *
	 * @param address
	 *            the base memory address as an int
	 * @param value
	 *            the 32-bit float value to store
	 * @param offset
	 *            the offset from the base address as an int
	 * @param memory
	 *            the WasmMemory32 instance to store to
	 */
	public static void storeF32(int address, float value, int offset, WasmMemory32 memory) {
		memory.storeF32(effectiveAddress(address, offset), value);
	}

	/**
	 * Loads a 64-bit double from memory at the specified address.
	 *
	 * @param address
	 *            the memory address as an int
	 * @param memory
	 *            the WasmMemory32 instance to load from
	 * @return the 64-bit double value at the specified memory location
	 */
	public static double loadF64(int address, WasmMemory32 memory) {
		return memory.loadF64(address);
	}

	/**
	 * Loads a 64-bit double from memory at the specified address with offset.
	 *
	 * @param address
	 *            the base memory address as an int
	 * @param offset
	 *            the offset from the base address as an int
	 * @param memory
	 *            the WasmMemory32 instance to load from
	 * @return the 64-bit double value at the specified memory location
	 */
	public static double loadF64(int address, int offset, WasmMemory32 memory) {
		return memory.loadF64(effectiveAddress(address, offset));
	}

	/**
	 * Stores a 64-bit double to memory at the specified address.
	 *
	 * @param address
	 *            the memory address as an int
	 * @param value
	 *            the 64-bit double value to store
	 * @param memory
	 *            the WasmMemory32 instance to store to
	 */
	public static void storeF64(int address, double value, WasmMemory32 memory) {
		memory.storeF64(address, value);
	}

	/**
	 * Stores a 64-bit double to memory at the specified address with offset.
	 *
	 * @param address
	 *            the base memory address as an int
	 * @param value
	 *            the 64-bit double value to store
	 * @param offset
	 *            the offset from the base address as an int
	 * @param memory
	 *            the WasmMemory32 instance to store to
	 */
	public static void storeF64(int address, double value, int offset, WasmMemory32 memory) {
		memory.storeF64(effectiveAddress(address, offset), value);
	}

	/**
	 * Loads a 128-bit vector from memory at the specified address.
	 *
	 * @param address
	 *            the memory address as an int
	 * @param memory
	 *            the WasmMemory32 instance to load from
	 * @return a V128 instance containing the 128-bit vector value
	 */
	public static V128 loadV128(int address, WasmMemory32 memory) {
		return memory.loadV128(address);
	}

	/**
	 * Loads a 128-bit vector from memory at the specified address with offset.
	 *
	 * @param address
	 *            the base memory address as an int
	 * @param offset
	 *            the offset from the base address as an int
	 * @param memory
	 *            the WasmMemory32 instance to load from
	 * @return a V128 instance containing the 128-bit vector value
	 */
	public static V128 loadV128(int address, int offset, WasmMemory32 memory) {
		return memory.loadV128(effectiveAddress(address, offset));
	}

	/**
	 * Stores a 128-bit vector to memory at the specified address.
	 *
	 * @param address
	 *            the memory address as an int
	 * @param value
	 *            the V128 instance containing the 128-bit vector value to store
	 * @param memory
	 *            the WasmMemory32 instance to store to
	 */
	public static void storeV128(int address, V128 value, WasmMemory32 memory) {
		memory.storeV128(address, value);
	}

	/**
	 * Stores a 128-bit vector to memory at the specified address with offset.
	 *
	 * @param address
	 *            the base memory address as an int
	 * @param value
	 *            the V128 instance containing the 128-bit vector value to store
	 * @param offset
	 *            the offset from the base address as an int
	 * @param memory
	 *            the WasmMemory32 instance to store to
	 */
	public static void storeV128(int address, V128 value, int offset, WasmMemory32 memory) {
		memory.storeV128(effectiveAddress(address, offset), value);
	}

}
