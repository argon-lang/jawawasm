package dev.argon.jawawasm.format.types;

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * A recursive type.
 * @param subtypes The subtypes.
 */
public record RecursiveType(ImmutableList<SubType> subtypes) {
}
