package jvmwasm.format;

import java.nio.charset.CharacterCodingException;

public class ModuleFormatException extends Exception {
	public ModuleFormatException(String message) {
		super(message);
	}

	public ModuleFormatException(String message, Throwable cause) {
		super(message, cause);
	}
}
