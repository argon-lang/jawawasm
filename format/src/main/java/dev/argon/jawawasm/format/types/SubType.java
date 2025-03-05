package dev.argon.jawawasm.format.types;

import dev.argon.jawawasm.format.modules.TypeIdx;

import java.util.List;

public record SubType(
	boolean isFinal,
	List<? extends TypeIdx> superTypes,
	CompositeType compositeType
) {
}
