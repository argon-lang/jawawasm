package dev.argon.jawawasm.engine;

import dev.argon.jawawasm.format.types.GlobalType;
import dev.argon.jawawasm.format.types.Mut;

/**
 * A WebAssembly global.
 */
public final class WasmGlobal implements WasmExport {
	/**
	 * Create a global.
	 * @param type The global type.
	 * @param value The value.
	 */
	public WasmGlobal(GlobalType type, Object value) {
		this.type = type;
		this.value = value;
	}


	private final GlobalType type;
	private volatile Object value;

	/**
	 * Gets the global type.
	 * @return The global type.
	 */
	public GlobalType type() {
		return type;
	}

	/**
	 * Gets the global value.
	 * @return The global value.
	 */
	public Object get() {
		return value;
	}

	/**
	 * Sets the global value.
	 * @param value The global value.
	 */
	public void set(Object value) {
		if(type.mutability() == Mut.Const) {
			throw new IllegalStateException();
		}

		this.value = value;
	}
}
