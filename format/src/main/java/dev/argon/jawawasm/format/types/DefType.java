package dev.argon.jawawasm.format.types;

/**
 * A WebAssembly defined type.
 * @param recursiveType The recursive type that defines this type.
 * @param index The index into the recursive type.
 */
public record DefType(RecursiveType recursiveType, int index) implements HeapType, ExternalType {
}
