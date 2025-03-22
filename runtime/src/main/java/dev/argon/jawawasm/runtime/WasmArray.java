package dev.argon.jawawasm.runtime;

public non-sealed interface WasmArray extends WasmEq {
	/**
	 * Gets the length of the array.
	 * @return The length.
	 */
	int length();
}
