package dev.argon.jawawasm.engine;

/**
 * Indicates that an indirect call has the incorrect type for the specified function.
 */
public class IndirectCallTypeMismatchException extends Exception {
	/**
	 * Creates a IndirectCallTypeMismatchException.
	 */
	public IndirectCallTypeMismatchException() {}

	/**
	 * Creates a IndirectCallTypeMismatchException.
	 * @param message The error message.
	 */
	public IndirectCallTypeMismatchException(String message) {
		super(message);
	}
}
