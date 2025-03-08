package dev.argon.jawawasm.format.types;

/**
 * The type of a field.
 * @param storageType The field's storage type.
 * @param mut The mutability of the field.
 */
public record FieldType(StorageType storageType, Mut mut) {
}
