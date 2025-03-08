package dev.argon.jawawasm.format.types;

/**
 * A WebAssembly aggregate type.
 */
public sealed interface AggregateType extends CompositeType permits StructType, ArrayType {
}
