package dev.argon.jawawasm.engine.interpreter;

import dev.argon.jawawasm.format.modules.Module;
import dev.argon.jawawasm.runtime.*;

import java.lang.foreign.Arena;
import java.util.concurrent.ExecutionException;

/**
 * A WebAssembly engine
 */
public class Engine {
	/**
	 * Create an engine.
	 */
	public Engine(MemoryAllocator allocator) {
		this.allocator = allocator;
	}

	private final MemoryAllocator allocator;

	MemoryAllocator getAllocator() {
		return allocator;
	}

	/**
	 * Instantiates a WebAssembly module.
	 * @param module The module to instantiate.
	 * @param resolver The resolver to use.
	 * @return The instantiated module.
	 * @throws ExecutionException when an error occurs executing WebAssembly code.
	 * @throws ModuleLinkException when an error occurs while linking.
	 */
	public InstantiatedModule instantiateModule(Module module, ModuleResolver resolver) throws ExecutionException, ModuleLinkException {
		return new InstantiatedModule(this, module, resolver);
	}

}
