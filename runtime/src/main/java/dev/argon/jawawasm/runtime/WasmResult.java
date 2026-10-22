package dev.argon.jawawasm.runtime;

/**
 * Marker interface for a WebAssembly result carrier. Module loaders infer the
 * result shape from the static {@code of} factory and validate the {@code get}
 * method and nested {@code EndResult} class.
 */
public interface WasmResult {}
