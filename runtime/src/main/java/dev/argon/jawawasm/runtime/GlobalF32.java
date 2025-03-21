package dev.argon.jawawasm.runtime;

/**
 * A mutable global f32.
 */
public class GlobalF32 {
	/**
	 * Creates a global f32
	 * @param value The initial value.
	 */
	public GlobalF32(float value) {
		this.value = value;
	}

	private float value;

	/**
	 * Gets the global value.
	 * @return The value;
	 */
	public float get() {
		return value;
	}

	/**
	 * Sets the global value.
	 * @param value The new value
	 */
	public void set(float value) {
		this.value = value;
	}
}
