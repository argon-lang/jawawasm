package dev.argon.jawawasm.app;

import dev.argon.jawawasm.format.ModuleFormatException;

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
