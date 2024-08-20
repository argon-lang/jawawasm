package dev.argon.jawawasm.format.types;

/**
 * Reference type
 */
public record RefType(
	boolean isNullable,
	HeapType heapType
) implements ValType {}
