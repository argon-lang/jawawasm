package dev.argon.jawawasm.engine.interpreter;

/**
 * A trap thrown when a WebAssembly cast fails.
 */
public class WebAssemblyCastTrap extends RuntimeException {
	/**
	 * Create a cast trap exception.
	 */
	public WebAssemblyCastTrap() {}
}
