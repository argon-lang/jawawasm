package dev.argon.jawawasm.runtime;

import java.lang.foreign.Arena;

/**
 * An allocator that uses an Arena to allocate memory segments.
 */
public class ArenaMemoryAllocator extends MemoryAllocator {
	/**
	 * Creates an allocator
	 * @param arena The arena used to allocate memory
	 */
	public ArenaMemoryAllocator(Arena arena) {
		this.arena = arena;
	}

	private final Arena arena;

	@Override
	public WasmMemoryNoResize allocateMemory(AddrType addrType, long pages) {
		return new WasmMemoryImpl(addrType, arena.allocate(Math.multiplyExact(pages, Util.PAGE_SIZE)));
	}
}
