package dev.argon.jawawasm.engine.compiler;

import org.jspecify.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.Supplier;

/**
 * Realization of an array type.
 * @param classDesc The array class.
 * @param elementType The element class.
 * @param superType The realization of the supertype of this array type.
 */
public record ArrayTypeRealization(
	ClassDesc classDesc,
	Supplier<ClassDesc> elementType,
	Supplier<@Nullable ArrayTypeRealization> superType
) implements DefTypeRealization {
}
