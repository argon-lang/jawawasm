package dev.argon.jawawasm.engine.compiler;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.function.Supplier;

public record ArrayTypeRealization(
	ClassDesc classDesc,
	Supplier<ClassDesc> elementType
) implements DefTypeRealization {
}
