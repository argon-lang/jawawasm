package dev.argon.jawawasm.engine.compiler;

import java.lang.constant.ClassDesc;
import java.util.function.Supplier;

record FuncTypeRealization(
	ClassDesc classDesc,
	String methodName,
	Supplier<MethodTypeRealization> methodType
) implements DefTypeRealization {}
