package dev.argon.jawawasm.engine;

import dev.argon.jawawasm.format.modules.Data;
import dev.argon.jawawasm.format.types.MemType;

/**
 * A WebAssembly memory space.
 */
public sealed interface WasmMemory extends WasmMemoryNoResize, WasmExport permits WasmMemoryMeta {

	/**
	 * Gets the memory type.
	 * @return The memory type.
	 */
	MemType type();

	/**
	 * Grow the memory.
	 * @param pages The number of pages by which to grow the memory.
	 * @return The old number of pages.
	 */
	int grow(int pages);

	/**
	 * Creates a WasmMemory
	 * @param engine The engine that will be using the memory.
	 * @param memType The type of the memory object.
	 * @return The created memory.
	 */
	public static WasmMemory create(Engine engine, MemType memType) {
		return new WasmMemoryMeta(engine, memType.limits().max(), engine.allocateMemory(memType.limits().min()));
	}

	/**
	 * Fills a range of memory with a byte value.
	 * @param d The starting address.
	 * @param val The value to fill.
	 * @param n The number of bytes to fill.
	 * @param memory The memory to fill.
	 */
	public static void fill(int d, byte val, int n, WasmMemory memory) {
		if(!Util.sumInRange(d, n, memory.byteSize())) {
			throw new IndexOutOfBoundsException();
		}

		while(n != 0) {
			memory.storeI8(d, val);
			++d;
			--n;
		}
	}

	/**
	 * Copies data within a memory.
	 * @param d The start address of the destination.
	 * @param s The start address of the source.
	 * @param n The number of bytes to copy.
	 * @param memory The memory to copy data within.
	 */
	public static void copy(int d, int s, int n, WasmMemory memory) {
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

	/**
	 * Initialize memory with a data segment.
	 * @param d The start address of the destination.
	 * @param s The start address of the source.
	 * @param n The number of bytes to copy.
	 * @param memory The destination memory.
	 * @param data The source data segment.
	 */
	public static void init(int d, int s, int n, WasmMemory memory, Data data) {
		memory.init(d, s, n, data);
	}
}
