package dev.argon.jawawasm.runtime;

/**
 * Represents a 31-bit integer.
 */
public final class I31 implements WasmEq {
	/**
	 * Create a 31-bit value.
	 * @param value The value. The top bit is discarded.
	 */
	public I31(int value) {
		this.value = (value << 1) >> 1;
	}


	private final int value;

	/**
	 * Gets the value interpreted as a signed integer.
	 * @return The value.
	 */
	public int signedValue() {
		return value;
	}

	/**
	 * Gets the value interpreted as an unsigned integer.
	 * @return The value.
	 */
	public int unsignedValue() {
		return value & 0x7FFFFFFF;
	}

	@Override
	public boolean equals(Object obj) {
		if(!(obj instanceof I31 other)) {
			return false;
		}

		return value == other.value;
	}

	@Override
	public int hashCode() {
		return value;
	}

	@Override
	public String toString() {
		return "I31(" + value + ")";
	}
}
