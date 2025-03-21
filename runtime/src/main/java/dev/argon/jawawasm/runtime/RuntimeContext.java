package dev.argon.jawawasm.runtime;

/**
 * A context provided to WebAssembly modules when instantiated.
 */
public interface RuntimeContext {

	/**
	 * Gets the memory allocator for this context.
	 * @return The memory allocator.
	 */
	MemoryAllocator allocator();

}
