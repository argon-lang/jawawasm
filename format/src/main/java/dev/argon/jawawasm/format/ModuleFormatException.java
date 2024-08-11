package dev.argon.jawawasm.format;

import java.nio.charset.CharacterCodingException;

/**
 * Indicates that a WebAssembly module is malformed.
 */
public class ModuleFormatException extends Exception {
	/**
	 * Creates a ModuleFormatException
	 * @param message The error message.
	 */
	public ModuleFormatException(String message) {
		super(message);
	}


	/**
	 * Creates a ModuleFormatException
	 * @param message The error message.
	 * @param cause The underlying error.
	 */
	public ModuleFormatException(String message, Throwable cause) {
		super(message, cause);
	}
}
