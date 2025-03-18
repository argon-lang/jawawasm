package dev.argon.jawawasm.engine.interpreter;

import dev.argon.jawawasm.format.types.HeapType;

/**
 * Base type for WebAssembly objects.
 */
public sealed interface DynamicWasmObject permits DynamicWasmEq, DynamicWasmFunction, DynamicWebAssemblyException {
	/**
	 * Gets the heap type.
	 * @return The heap type of this object.
	 */
	HeapType heapType();
}
