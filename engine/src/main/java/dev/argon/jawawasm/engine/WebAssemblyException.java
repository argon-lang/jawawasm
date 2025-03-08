package dev.argon.jawawasm.engine;

/**
 * An exception thrown by WebAssembly.
 */
public final class WebAssemblyException extends Exception {
	/**
	 * Create an exception.
	 * @param tag The tag type of the exception.
	 * @param values The exception payload.
	 */
	public WebAssemblyException(WasmTag tag, Object[] values) {
		super("Exception thrown by WebAssembly code");
		this.tag = tag;
		this.values = values;
	}

	/**
	 * The exception tag.
	 */
	private final WasmTag tag;

	/**
	 * The payload.
	 */
	private final Object[] values;

	/**
	 * Gets the tag of this exception.
	 * @return The exception type.
	 */
	public WasmTag getTag() {
		return tag;
	}

	/**
	 * Gets the payload.
	 * @return The payload.
	 */
	public Object[] getValues() {
		return values;
	}
}
