package dev.argon.jawawasm.app;


/**
 * A script execution failed.
 */
public class ScriptExecutionException extends Exception {
	/**
	 * Creates a ScriptExecutionException.
	 */
	public ScriptExecutionException() {}

	/**
	 * Creates a ScriptExecutionException.
	 * @param message The error message.
	 */
	public ScriptExecutionException(String message) {
		super(message);
	}

	/**
	 * Creates a ScriptExecutionException.
	 * @param cause The underlying error.
	 */
	public ScriptExecutionException(Throwable cause) {
		super(cause);
	}

	/**
	 * Creates a ModuleLinkException.
	 * @param message The error message.
	 * @param cause The underlying error.
	 */
	public ScriptExecutionException(String message, Throwable cause) {
		super(message, cause);
	}
}
