package dev.argon.jawawasm.engine.validator;

/**
 * Indicates module validation failed.
 */
public class ValidationException extends Exception {
	/**
	 * Create a validation exception.
	 * @param message The error message.
	 */
	public ValidationException(String message) {
		super(message);
	}
}
