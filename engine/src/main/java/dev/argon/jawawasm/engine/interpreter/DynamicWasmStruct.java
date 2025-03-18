package dev.argon.jawawasm.engine.interpreter;

import dev.argon.jawawasm.format.types.DefType;
import dev.argon.jawawasm.format.types.HeapType;
import org.jspecify.annotations.Nullable;

/**
 * A WebAssembly struct value.
 */
public final class DynamicWasmStruct implements DynamicWasmEq {
	DynamicWasmStruct(DefType type, @Nullable Object[] values) {
		this.type = type;
		this.values = values;
	}

	private final DefType type;
	private final @Nullable Object[] values;

	@Override
	public HeapType heapType() {
		return type;
	}

	/**
	 * Gets the value of the field at an index.
	 * @param index The index.
	 * @return The value of the field at the index.
	 */
	public @Nullable Object getField(int index) {
		return values[index];
	}

	/**
	 * Sets the value of the field at an index.
	 * @param index The index.
	 * @param value The new field value.
	 */
	public void setField(int index, @Nullable Object value) {
		values[index] = value;
	}
}
