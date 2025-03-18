package dev.argon.jawawasm.runtime;

/**
 * Base type for WebAssembly objects.
 */
public sealed interface WasmObject permits WasmEq, WasmFunction, WebAssemblyException {
}
