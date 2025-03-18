package dev.argon.jawawasm.engine.interpreter;

import dev.argon.jawawasm.runtime.WasmMemory;

public record WasmMemoryExport(WasmMemory memory) implements WasmExport {


}
