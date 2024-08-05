package dev.argon.jvmwasm.engine;

/**
 * WebAssembly elements.
 */
public class WasmElements {
	/**
	 * Create elements.
	 * @param values The element vaules.
	 */
	public WasmElements(Object[] values) {
		this.values = values;
	}

	private final Object[] values;

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
	public Object get(int i) {
		return values[i];
	}
}
