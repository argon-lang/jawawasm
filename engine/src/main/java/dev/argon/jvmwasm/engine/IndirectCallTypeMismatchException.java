package dev.argon.jvmwasm.engine;

/**
 * Indicates that an indirect call has the incorrect type for the specified function.
 */
public class IndirectCallTypeMismatchException extends Exception {
	/**
	 * Creates a IndirectCallTypeMismatchException.
	 */
	public IndirectCallTypeMismatchException() {}
}
