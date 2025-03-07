package dev.argon.jawawasm.engine;

/**
 * Indicates that an indirect call has the incorrect type for the specified function.
 */
public class IndirectCallTypeMismatchTrap extends RuntimeException {
	/**
	 * Creates a IndirectCallTypeMismatchException.
	 */
	public IndirectCallTypeMismatchTrap() {}

	/**
	 * Creates a IndirectCallTypeMismatchException.
	 * @param message The error message.
	 */
	public IndirectCallTypeMismatchTrap(String message) {
		super(message);
	}
}
