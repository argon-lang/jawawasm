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
		this(message, message);
	}

	public ValidationException(String testMessage, String message) {
		super(message);
		this.testMessage = testMessage;
	}

	private final String testMessage;

	public String getTestMessage() {
		return testMessage;
	}
}
