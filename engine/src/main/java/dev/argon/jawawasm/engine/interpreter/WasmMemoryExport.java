package dev.argon.jawawasm.engine.interpreter;

import dev.argon.jawawasm.runtime.WasmMemory;

/**
 * An exported WasmMemory
 * @param memory The exported memory.
 */
public record WasmMemoryExport(WasmMemory memory) implements WasmExport {


}
