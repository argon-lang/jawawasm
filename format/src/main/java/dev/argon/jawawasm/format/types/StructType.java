package dev.argon.jawawasm.format.types;

import java.util.List;

/**
 * A WebAssembly struct type.
 * @param fields The fields of the struct.
 */
public record StructType(List<? extends FieldType> fields) implements AggregateType {
}
