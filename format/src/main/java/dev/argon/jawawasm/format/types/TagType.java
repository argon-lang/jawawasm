package dev.argon.jawawasm.format.types;

import dev.argon.jawawasm.format.modules.TypeIdx;

public record TagType(TypeIdx funcType) implements ExternalType {
}
