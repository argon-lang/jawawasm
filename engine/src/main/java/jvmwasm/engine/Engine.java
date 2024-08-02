package jvmwasm.engine;

import jvmwasm.format.modules.Module;

import java.lang.foreign.Arena;

public class Engine implements AutoCloseable {
	public Engine() {
		arena = Arena.ofShared();
	}

	private final Arena arena;

	private int maxMemory = 0;

	public synchronized int getMaxMemory() {
		return maxMemory;
	}

	public synchronized void setMaxMemory(int maxMemory) {
		this.maxMemory = maxMemory;
	}



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
