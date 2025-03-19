package dev.argon.jawawasm.engine;

import dev.argon.jawawasm.engine.interpreter.WasmModule;
import dev.argon.jawawasm.runtime.ModuleResolutionException;

/**
 * Resolves modules by name.
 */
public interface ModuleResolver<Mod> {
	/**
	 * Resolve a module.
	 * @param name The name of the module.
	 * @return The module.
	 * @throws ModuleResolutionException if the module could not be resolved.
	 */
	Mod resolve(Mod importer, String name) throws ModuleResolutionException;
}
