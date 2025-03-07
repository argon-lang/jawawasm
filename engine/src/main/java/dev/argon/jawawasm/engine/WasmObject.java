package dev.argon.jawawasm.engine;

import dev.argon.jawawasm.format.types.HeapType;

public sealed interface WasmObject permits WasmEq, WasmFunction {
	HeapType heapType();
}
