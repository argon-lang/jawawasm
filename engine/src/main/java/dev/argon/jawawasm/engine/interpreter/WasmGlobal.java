package dev.argon.jawawasm.engine.interpreter;

import dev.argon.jawawasm.format.types.GlobalType;
import dev.argon.jawawasm.format.types.Mut;
import org.jspecify.annotations.Nullable;

/**
 * A WebAssembly global.
 */
public final class WasmGlobal implements WasmExport {
	/**
	 * Create a global.
	 * @param type The global type.
	 * @param value The value.
	 */
	public WasmGlobal(GlobalType type, @Nullable Object value) {
		this.type = type;
		this.value = value;
	}


	private final GlobalType type;
	private volatile @Nullable Object value;

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
	public @Nullable Object get() {
		return value;
	}

	/**
	 * Sets the global value.
	 * @param value The global value.
	 */
	public void set(@Nullable Object value) {
		if(type.mutability() == Mut.Const) {
			throw new IllegalStateException();
		}

		this.value = value;
	}
}
