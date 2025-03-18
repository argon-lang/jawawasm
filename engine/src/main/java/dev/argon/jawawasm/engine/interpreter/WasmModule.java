package dev.argon.jawawasm.engine.interpreter;

import org.jspecify.annotations.Nullable;

/**
 * A WebAssembly module.
 */
public interface WasmModule {
	/**
	 * Get an export.
	 * @param name The export name.
	 * @return The export.
	 */
	@Nullable WasmExport getExport(String name);
}
