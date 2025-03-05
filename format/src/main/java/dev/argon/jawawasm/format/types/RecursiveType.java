package dev.argon.jawawasm.format.types;

import java.util.List;

public record RecursiveType(List<? extends SubType> subtypes) {
}
