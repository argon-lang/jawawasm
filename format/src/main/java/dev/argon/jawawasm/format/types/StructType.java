package dev.argon.jawawasm.format.types;

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * A WebAssembly struct type.
 * @param fields The fields of the struct.
 */
public record StructType(ImmutableList<FieldType> fields) implements AggregateType {
}
