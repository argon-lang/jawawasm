package dev.argon.jawawasm.format.types;

public sealed interface AggregateType extends CompositeType permits StructType, ArrayType {
}
