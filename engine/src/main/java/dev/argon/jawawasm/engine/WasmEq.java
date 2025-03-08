package dev.argon.jawawasm.engine;

/**
 * Represents a Wasm heap value that can be compared with equality.
 */
public sealed interface WasmEq extends WasmObject permits I31, WasmArray, WasmStruct {
}
