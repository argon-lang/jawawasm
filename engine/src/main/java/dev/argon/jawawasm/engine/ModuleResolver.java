package dev.argon.jawawasm.engine;

import dev.argon.jawawasm.engine.interpreter.WasmModule;
import dev.argon.jawawasm.runtime.ModuleResolutionException;

/**
 * Resolves modules by name.
 * @param <Mod> The type of the module to be resolved.
 */
public interface ModuleResolver<Mod> {
	/**
	 * Resolve a module.
	 * @param name The name of the module.
	 * @return The module.
	 * @throws ModuleResolutionException if the module could not be resolved.
	 */
	Mod resolve(String name) throws ModuleResolutionException;
}
