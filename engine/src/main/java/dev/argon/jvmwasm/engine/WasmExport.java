package dev.argon.jvmwasm.engine;

/**
 * A WebAssembly export.
 */
public sealed interface WasmExport permits WasmFunction, WasmTable, WasmMemory, WasmGlobal {
}
