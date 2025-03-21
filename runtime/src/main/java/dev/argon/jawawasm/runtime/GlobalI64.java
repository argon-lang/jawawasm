package dev.argon.jawawasm.runtime;

/**
 * A mutable global i64.
 */
public class GlobalI64 {
	/**
	 * Creates a global i64
	 * @param value The initial value.
	 */
	public GlobalI64(long value) {
		this.value = value;
	}

	/**
	 * The global value.
	 */
	private long value;

	/**
	 * Gets the global value.
	 * @return The value;
	 */
	public long get() {
		return value;
	}

	/**
	 * Sets the global value.
	 * @param value The new value
	 */
	public void set(long value) {
		this.value = value;
	}
}
