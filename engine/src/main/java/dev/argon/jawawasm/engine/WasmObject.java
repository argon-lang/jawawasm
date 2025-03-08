package dev.argon.jawawasm.engine;

import dev.argon.jawawasm.format.types.HeapType;

/**
 * Base type for WebAssembly objects.
 */
public sealed interface WasmObject permits WasmEq, WasmFunction {
	/**
	 * Gets the heap type.
	 * @return The heap type of this object.
	 */
	HeapType heapType();
}
