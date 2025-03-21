package dev.argon.jawawasm.runtime;

/**
 * A mutable global i32.
 */
public class GlobalI32 {
	/**
	 * Creates a global i32
	 * @param value The initial value.
	 */
	public GlobalI32(int value) {
		this.value = value;
	}

	private int value;

	/**
	 * Gets the global value.
	 * @return The value;
	 */
	public int get() {
		return value;
	}

	/**
	 * Sets the global value.
	 * @param value The new value
	 */
	public void set(int value) {
		this.value = value;
	}
}
