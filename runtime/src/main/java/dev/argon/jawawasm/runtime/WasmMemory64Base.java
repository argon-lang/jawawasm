package dev.argon.jawawasm.runtime;

/**
 * A WebAssembly memory with 64-bit addresses.
 */
public sealed interface WasmMemory64Base extends WasmMemory permits WasmMemory64 {}
