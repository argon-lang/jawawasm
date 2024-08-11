package dev.argon.jawawasm.format.types;

import org.jspecify.annotations.Nullable;

/**
 * Specifies limits for sizes.
 * @param min The minimum size.
 * @param max The maximum size.
 */
public record Limits(int min, @Nullable Integer max) {
}
