package dev.argon.jawawasm.format.types;

/**
 * Table type
 * @param limits Table size limits.
 * @param elementType Table element type.
 */
public record TableType(Limits limits, RefType elementType) {
}
