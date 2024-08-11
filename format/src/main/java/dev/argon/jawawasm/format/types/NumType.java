package dev.argon.jawawasm.format.types;

/**
 * Numeric types
 */
public enum NumType implements ValType {
	/**
	 * 32-bit integer
	 */
    I32,
	/**
	 * 64-bit integer
	 */
    I64,
	/**
	 * 32-bit float
	 */
    F32,
	/**
	 * 64-bit float
	 */
    F64,
}
