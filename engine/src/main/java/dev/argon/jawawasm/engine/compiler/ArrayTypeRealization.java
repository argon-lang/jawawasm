package dev.argon.jawawasm.engine.compiler;

import org.jspecify.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.function.Supplier;

/**
 * Realization of an array type.
 * 
 * @param classDesc
 *            The array class.
 * @param elementType
 *            The element class.
 * @param getMethodName
 *            The element accessor method name.
 * @param setMethodName
 *            The element mutator method name, or null for immutable arrays.
 * @param superType
 *            The realization of the supertype of this array type.
 */
public record ArrayTypeRealization(
	ClassDesc classDesc,
	Supplier<ClassDesc> elementType,
	String getMethodName,
	@Nullable String setMethodName,
	Supplier<@Nullable ArrayTypeRealization> superType
) implements DefTypeRealization {}
