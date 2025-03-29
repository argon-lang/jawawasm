package dev.argon.jawawasm.engine.compiler;

import org.jspecify.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.function.Supplier;

public record ArrayTypeRealization(
	ClassDesc classDesc,
	Supplier<ClassDesc> elementType,
	Supplier<@Nullable ArrayTypeRealization> superType
) implements DefTypeRealization {
}
