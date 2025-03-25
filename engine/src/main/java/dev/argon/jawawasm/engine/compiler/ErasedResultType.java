package dev.argon.jawawasm.engine.compiler;

import com.google.common.collect.ImmutableList;

import java.lang.classfile.TypeKind;

public record ErasedResultType(ImmutableList<TypeKind> types) {
}
