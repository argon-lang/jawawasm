package dev.argon.jawawasm.runtime;

/**
 * A mutable global f64.
 */
public class GlobalF64 {
	/**
	 * Creates a global f64
	 * @param value The initial value.
	 */
	public GlobalF64(double value) {
		this.value = value;
	}

	private double value;

	/**
	 * Gets the global value.
	 * @return The value;
	 */
	public double get() {
		return value;
	}

	/**
	 * Sets the global value.
	 * @param value The new value
	 */
	public void set(double value) {
		this.value = value;
	}
}
