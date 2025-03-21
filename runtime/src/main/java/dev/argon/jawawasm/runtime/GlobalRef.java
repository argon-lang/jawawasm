package dev.argon.jawawasm.runtime;

import org.jspecify.annotations.Nullable;

/**
 * A mutable global for objects.
 * @param <T> The type of the global.
 */
public class GlobalRef<T extends @Nullable Object> {
	/**
	 * Creates a global
	 * @param value The initial value.
	 */
	public GlobalRef(T value) {
		this.value = value;
	}

	private T value;

	/**
	 * Gets the global value.
	 * @return The value;
	 */
	public T get() {
		return value;
	}

	/**
	 * Sets the global value.
	 * @param value The new value
	 */
	public void set(T value) {
		this.value = value;
	}
}
