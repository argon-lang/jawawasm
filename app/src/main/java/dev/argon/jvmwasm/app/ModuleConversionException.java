package dev.argon.jvmwasm.app;

import dev.argon.jvmwasm.format.ModuleFormatException;

/**
 * Indicates that a text module could not be converted to binary.
 */
public class ModuleConversionException extends ModuleFormatException {
	/**
	 * Create a ModuleConversionException.
	 */
	public ModuleConversionException() {
		super("Could not convert text module to binary");
	}
}
