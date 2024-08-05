package dev.argon.jvmwasm.engine;

/**
 * Resolves modules by name.
 */
public interface ModuleResolver {
	/**
	 * Resolve a module.
	 * @param name The name of the module.
	 * @return The module.
	 * @throws Throwable if an error occurs.
	 */
	WasmModule resolve(String name) throws Throwable;
}
