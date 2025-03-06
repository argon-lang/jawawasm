package dev.argon.jawawasm.engine;

import dev.argon.jawawasm.format.types.DefType;
import dev.argon.jawawasm.format.types.FuncType;

public final class WasmTag implements WasmExport {
	public WasmTag(DefType type, FuncType funcType) {
		this.type = type;
		this.funcType = funcType;
	}

	private final DefType type;
	private final FuncType funcType;


	public DefType type() {
		return type;
	}

	public FuncType funcType() {
		return funcType;
	}
}
