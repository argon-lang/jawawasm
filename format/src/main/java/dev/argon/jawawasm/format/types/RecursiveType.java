package dev.argon.jawawasm.format.types;

import java.util.List;

/**
 * A recursive type.
 * @param subtypes The subtypes.
 */
public record RecursiveType(List<? extends SubType> subtypes) {
}
