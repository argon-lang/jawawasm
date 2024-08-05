package dev.argon.jvmwasm.format.types;

/**
 * The type of a memory.
 * @param limits The limits on the memory sizes.
 */
public record MemType(Limits limits) {
}
