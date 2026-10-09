package dev.argon.jawawasm.runtime;

/**
 * A WebAssembly memory with either 32-bit or 64-bit addresses.
 */
public sealed interface WasmMemory permits WasmMemory32Base, WasmMemory64Base {}
