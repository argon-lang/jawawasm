package dev.argon.jawawasm.format.types;

import java.util.List;

/**
 * A Sub type within a recursive type.
 * @param isFinal Indicates the type is final.
 * @param superTypes The super type of the sub type.
 * @param compositeType The composite type definition.
 */
public record SubType(
	boolean isFinal,
	List<? extends HeapType> superTypes,
	CompositeType compositeType
) {
}
