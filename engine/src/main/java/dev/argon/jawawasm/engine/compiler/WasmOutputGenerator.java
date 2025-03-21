package dev.argon.jawawasm.engine.compiler;

/**
 * A generator for output from the WebAssembly compiler.
 */
public sealed interface WasmOutputGenerator permits WasmClassGenerator, WasmResourceGenerator {
}
