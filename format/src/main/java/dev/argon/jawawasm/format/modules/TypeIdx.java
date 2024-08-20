package dev.argon.jawawasm.format.modules;

import dev.argon.jawawasm.format.types.HeapType;

/**
 * A type index.
 * @param index The index.
 */
public record TypeIdx(int index) implements HeapType {
}
