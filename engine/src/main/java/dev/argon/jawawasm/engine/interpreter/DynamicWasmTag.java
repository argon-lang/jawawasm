package dev.argon.jawawasm.engine.interpreter;

import dev.argon.jawawasm.format.types.DefType;
import dev.argon.jawawasm.format.types.FuncType;

/**
 * Represents a tag.
 */
public final class DynamicWasmTag implements WasmExport {
	/**
	 * Create a tag.
	 * @param type The defined type of the tag.
	 * @param funcType The function type of the tag.
	 */
	public DynamicWasmTag(DefType type, FuncType funcType) {
		this.type = type;
		this.funcType = funcType;
	}

	private final DefType type;
	private final FuncType funcType;

	/**
	 * Gets the defined type for the signature of the tag.
	 * @return The defined type.
	 */
	public DefType type() {
		return type;
	}

	/**
	 * Gets the function type for the signature of the tag.
	 * @return The function type.
	 */
	public FuncType funcType() {
		return funcType;
	}
}
