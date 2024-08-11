package dev.argon.jawawasm.format.modules;

import dev.argon.jawawasm.format.types.MemType;

/**
 * A memory section.
 * @param type The type of the memory.
 */
public record Mem(MemType type) {
}
