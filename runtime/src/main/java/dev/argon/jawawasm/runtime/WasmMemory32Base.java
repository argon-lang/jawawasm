package dev.argon.jawawasm.runtime;

/**
 * A WebAssembly memory with 32-bit addresses.
 */
public sealed interface WasmMemory32Base extends WasmMemory permits WasmMemory32 {}
