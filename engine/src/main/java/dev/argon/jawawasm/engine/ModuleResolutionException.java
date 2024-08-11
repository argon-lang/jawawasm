package dev.argon.jawawasm.engine;

/**
 * Indicates an error that occurs during module resolution.
 */
public class ModuleResolutionException extends ModuleLinkException {
	/**
	 * Creates a ModuleResolutionException.
	 */
	public ModuleResolutionException() {}

	/**
	 * Creates a ModuleResolutionException.
	 * @param message The error message.
	 */
	public ModuleResolutionException(String message) {
		super(message);
	}
}
