package dev.argon.jawawasm.format.types;

public sealed interface CompositeType extends HeapType permits FuncType, AggregateType {
}
