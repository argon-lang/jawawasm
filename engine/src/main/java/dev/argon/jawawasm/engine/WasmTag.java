package dev.argon.jawawasm.engine;

import dev.argon.jawawasm.format.types.FuncType;

public final class WasmTag implements WasmExport {
	public WasmTag(FuncType type) {
		this.type = type;
	}

	private final FuncType type;

	public FuncType type() {
		return type;
	}
}
