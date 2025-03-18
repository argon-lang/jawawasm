package dev.argon.jawawasm.runtime;

public abstract class MemoryAllocator {

	private long maxMemory = 0;

	/**
	 * Get the maximum memory size in pages.
	 * @return The maximum memory size
	 */
	public synchronized long getMaxMemory() {
		return maxMemory;
	}

	/**
	 * Set the maximum memory size in pages.
	 * @param maxMemory The maximum memory size.
	 */
	public synchronized void setMaxMemory(long maxMemory) {
		this.maxMemory = maxMemory;
	}

	/**
	 * Allocate memory
	 * @param addrType The address type.
	 * @param pages The memory size in pages.
	 * @return The memory.
	 */
	public abstract WasmMemoryNoResize allocateMemory(AddrType addrType, long pages);
}
