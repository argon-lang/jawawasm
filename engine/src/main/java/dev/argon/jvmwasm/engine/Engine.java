package dev.argon.jvmwasm.engine;

import dev.argon.jvmwasm.format.modules.Module;

import java.lang.foreign.Arena;

/**
 * A WebAssembly engine
 */
public class Engine implements AutoCloseable {
	/**
	 * Create an engine.
	 */
	public Engine() {
		arena = Arena.ofShared();
	}

	private final Arena arena;

	private int maxMemory = 0;

	/**
	 * Get the maximum memory size.
	 * @return The maximum memory size
	 */
	synchronized int getMaxMemory() {
		return maxMemory;
	}

	/**
	 * Set the maximum memory seize.
	 * @param maxMemory The maximum memory size.
	 */
	public synchronized void setMaxMemory(int maxMemory) {
		this.maxMemory = maxMemory;
	}


	/**
	 * Instantiates a WebAssembly module.
	 * @param module The module to instantiate.
	 * @param resolver The resolver to use.
	 * @return The instantiated module.
	 * @throws Throwable when an error occurs, potentially thrown by the WebAssembly start function.
	 */
	public InstantiatedModule instantiateModule(Module module, ModuleResolver resolver) throws Throwable {
		return new InstantiatedModule(this, module, resolver);
	}

	WasmMemoryNoResize allocateMemory(int pages) {
		return new WasmMemoryImpl(arena.allocate((long)pages * Util.PAGE_SIZE));
	}

	@Override
	public void close() throws Exception {
		arena.close();
	}
}
