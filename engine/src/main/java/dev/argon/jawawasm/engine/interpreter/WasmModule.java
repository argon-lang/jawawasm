package dev.argon.jawawasm.engine.interpreter;

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
