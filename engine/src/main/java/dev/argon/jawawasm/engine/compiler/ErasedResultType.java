package dev.argon.jawawasm.engine.compiler;

import com.google.common.collect.ImmutableList;

import java.lang.classfile.TypeKind;

/**
 * The esasure of a result type.
 * @param types The kinds of the types of the result.
 */
public record ErasedResultType(ImmutableList<TypeKind> types) {
}
