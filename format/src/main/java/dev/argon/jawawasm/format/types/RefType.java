package dev.argon.jawawasm.format.types;

/**
 * Reference type
 * @param isNullable Indicates if the reference type is nullable.
 * @param heapType The heap type.
 */
public record RefType(
	boolean isNullable,
	HeapType heapType
) implements ValType {}
