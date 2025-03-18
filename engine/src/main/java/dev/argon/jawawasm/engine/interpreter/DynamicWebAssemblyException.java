package dev.argon.jawawasm.engine.interpreter;

import dev.argon.jawawasm.format.types.HeapType;
import dev.argon.jawawasm.runtime.WebAssemblyException;
import org.jspecify.annotations.Nullable;

/**
 * An exception thrown by WebAssembly.
 */
public final class DynamicWebAssemblyException extends WebAssemblyException implements DynamicWasmObject {
	/**
	 * Create an exception.
	 * @param tag The tag type of the exception.
	 * @param values The exception payload.
	 */
	public DynamicWebAssemblyException(DynamicWasmTag tag, @Nullable Object[] values) {
		this.tag = tag;
		this.values = values;
	}

	/**
	 * The exception tag.
	 */
	private final DynamicWasmTag tag;

	/**
	 * The payload.
	 */
	private final @Nullable Object[] values;

	/**
	 * Gets the tag of this exception.
	 * @return The exception type.
	 */
	public DynamicWasmTag getTag() {
		return tag;
	}

	/**
	 * Gets the payload.
	 * @return The payload.
	 */
	public @Nullable Object[] getValues() {
		return values;
	}

	@Override
	public HeapType heapType() {
		return HeapType.AbstractHeapType.EXN;
	}
}
