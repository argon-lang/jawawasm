package dev.argon.jawawasm.engine.interpreter;

import org.jspecify.annotations.Nullable;

/**
 * WebAssembly elements.
 */
public class WasmElements {
	/**
	 * Create elements.
	 * @param values The element vaules.
	 */
	public WasmElements(@Nullable Object[] values) {
		this.values = values;
	}

	private final @Nullable Object[] values;

	/**
	 * Gets the number of values.
	 * @return The number of values.
	 */
	public int size() {
		return values.length;
	}

	/**
	 * Gets an element.
	 * @param i The index.
	 * @return The element.
	 */
	public @Nullable Object get(int i) {
		return values[i];
	}
}
