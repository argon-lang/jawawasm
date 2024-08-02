package jvmwasm.engine;

public sealed interface WasmExport permits WasmFunction, WasmTable, WasmMemory, WasmGlobal {
}
