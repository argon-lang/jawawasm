package dev.argon.jawawasm.format.modules;

/**
 * A data section.
 * @param init The initial data.
 * @param mode The mode.
 */
public record Data(byte[] init, DataMode mode) {
}
