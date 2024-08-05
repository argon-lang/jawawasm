package dev.argon.jvmwasm.format.modules;

import dev.argon.jvmwasm.format.types.MemType;

/**
 * A memory section.
 * @param type The type of the memory.
 */
public record Mem(MemType type) {
}
