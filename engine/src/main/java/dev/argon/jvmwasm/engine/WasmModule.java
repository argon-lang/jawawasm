package dev.argon.jvmwasm.engine;

/**
 * A WebAssembly module.
 */
public interface WasmModule {
	/**
	 * Get an export.
	 * @param name The export name.
	 * @return The export.
	 */
	WasmExport getExport(String name);
}
