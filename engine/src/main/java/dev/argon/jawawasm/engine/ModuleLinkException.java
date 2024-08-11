package dev.argon.jawawasm.engine;

/**
 * Indicates an error occurred during module linking.
 */
public class ModuleLinkException extends Exception {
	/**
	 * Creates a ModuleLinkException.
	 */
	public ModuleLinkException() {}

	/**
	 * Creates a ModuleLinkException.
	 * @param message The error message.
	 */
	public ModuleLinkException(String message) {
		super(message);
	}

	/**
	 * Creates a ModuleLinkException.
	 * @param cause The underlying error.
	 */
	public ModuleLinkException(Throwable cause) {
		super(cause);
	}

	/**
	 * Creates a ModuleLinkException.
	 * @param message The error message.
	 * @param cause The underlying error.
	 */
	public ModuleLinkException(String message, Throwable cause) {
		super(message, cause);
	}
}
