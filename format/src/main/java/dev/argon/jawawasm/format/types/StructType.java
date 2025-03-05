package dev.argon.jawawasm.format.types;

import java.util.List;

public record StructType(List<? extends FieldType> fields) implements AggregateType {
}
