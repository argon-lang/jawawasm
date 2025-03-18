package dev.argon.jawawasm.engine.interpreter;

/**
 * A WebAssembly export.
 */
public sealed interface WasmExport permits DynamicWasmFunction, WasmTable, WasmMemoryExport, WasmGlobal, DynamicWasmTag {
}
