package dev.argon.jawawasm.engine;

import dev.argon.jawawasm.format.types.DefType;
import dev.argon.jawawasm.format.types.HeapType;

public final class WasmStruct implements WasmEq {
	WasmStruct(DefType type, Object[] values) {
		this.type = type;
		this.values = values;
	}

	private final DefType type;
	private final Object[] values;

	@Override
	public HeapType heapType() {
		return type;
	}

	public Object getField(int index) {
		return values[index];
	}

	public void setField(int index, Object value) {
		values[index] = value;
	}
}
