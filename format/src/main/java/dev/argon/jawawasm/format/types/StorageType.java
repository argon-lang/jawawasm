package dev.argon.jawawasm.format.types;

/**
 * A type that can be stored in a field.
 */
public sealed interface StorageType permits ValType, PackedType {
}
