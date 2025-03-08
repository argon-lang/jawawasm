package dev.argon.jawawasm.format.types;

/**
 * An index within a recursive type.
 * @param index The index.
 */
public record RecTypeIdx(int index) implements HeapType {
}
