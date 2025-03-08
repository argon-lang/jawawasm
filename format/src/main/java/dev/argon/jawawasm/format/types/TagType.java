package dev.argon.jawawasm.format.types;

import dev.argon.jawawasm.format.modules.TypeIdx;

/**
 * The type of the tag.
 * @param funcType The index of the function type describing this tag.
 */
public record TagType(TypeIdx funcType) implements ExternalType {
}
