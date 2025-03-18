package dev.argon.jawawasm.engine.interpreter;

/**
 * Represents a Wasm heap value that can be compared with equality.
 */
public sealed interface DynamicWasmEq extends DynamicWasmObject permits DynamicWasmArray, DynamicWasmStruct {
}
