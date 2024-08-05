package dev.argon.jvmwasm.engine;

/**
 * Resolves modules by name.
 */
public interface ModuleResolver {
	/**
	 * Resolve a module.
	 * @param name The name of the module.
	 * @return The module.
	 * @throws ModuleResolutionException if the module could not be resolved.
	 */
	WasmModule resolve(String name) throws ModuleResolutionException;
}
