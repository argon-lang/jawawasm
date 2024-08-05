package dev.argon.jvmwasm.engine;

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
}
