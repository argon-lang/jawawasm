package dev.argon.jawawasm.engine;

import dev.argon.jawawasm.format.types.DefType;
import dev.argon.jawawasm.format.types.HeapType;

/**
 * A WebAssembly struct value.
 */
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

	/**
	 * Gets the value of the field at an index.
	 * @param index The index.
	 * @return The value of the field at the index.
	 */
	public Object getField(int index) {
		return values[index];
	}

	/**
	 * Sets the value of the field at an index.
	 * @param index The index.
	 * @param value The new field value.
	 */
	public void setField(int index, Object value) {
		values[index] = value;
	}
}
