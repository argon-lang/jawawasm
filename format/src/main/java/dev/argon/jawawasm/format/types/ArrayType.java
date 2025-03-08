package dev.argon.jawawasm.format.types;

/**
 * A WebAssembly array type.
 * @param fieldType The element type.
 */
public record ArrayType(FieldType fieldType) implements AggregateType {
}
