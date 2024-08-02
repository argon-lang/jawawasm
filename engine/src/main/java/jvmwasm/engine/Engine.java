package jvmwasm.engine;

import jvmwasm.format.modules.Module;

import java.lang.foreign.MemorySession;

public class Engine implements AutoCloseable {
	public Engine() {
		session = MemorySession.openShared();
	}

	private final MemorySession session;

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
		return new WasmMemoryImpl(session.allocate((long)pages * Util.PAGE_SIZE));
	}

	@Override
	public void close() throws Exception {
		session.close();
	}
}
