package dev.argon.jawawasm.engine.compiler;

import org.jspecify.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.Optional;
import java.util.function.Supplier;

record FuncTypeRealization(
	ClassDesc classDesc,
	String methodName,
	Supplier<MethodTypeRealization> methodType,
	Supplier<@Nullable FuncTypeRealization> superType
) implements DefTypeRealization {}
