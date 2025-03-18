package dev.argon.jawawasm.runtime;

/**
 * An exception thrown by WebAssembly.
 */
public non-sealed abstract class WebAssemblyException extends Exception implements WasmObject {
	/**
	 * Create an exception.
	 */
	public WebAssemblyException() {
		super("Exception thrown by WebAssembly code");
	}
}
