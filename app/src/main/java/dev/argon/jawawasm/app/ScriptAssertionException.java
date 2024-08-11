package dev.argon.jawawasm.app;


/**
 * A script assertion failed.
 */
public class ScriptAssertionException extends ScriptExecutionException {
	/**
	 * Creates a ScriptAssertionException.
	 */
	public ScriptAssertionException() {}

	/**
	 * Creates a ScriptAssertionException.
	 * @param message The error message.
	 */
	public ScriptAssertionException(String message) {
		super(message);
	}

	/**
	 * Creates a ScriptAssertionException.
	 * @param cause The underlying error.
	 */
	public ScriptAssertionException(Throwable cause) {
		super(cause);
	}

	/**
	 * Creates a ScriptAssertionException.
	 * @param message The error message.
	 * @param cause The underlying error.
	 */
	public ScriptAssertionException(String message, Throwable cause) {
		super(message, cause);
	}
}
