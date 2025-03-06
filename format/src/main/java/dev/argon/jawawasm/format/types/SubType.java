package dev.argon.jawawasm.format.types;

import java.util.List;

public record SubType(
	boolean isFinal,
	List<? extends HeapType> superTypes,
	CompositeType compositeType
) {
}
