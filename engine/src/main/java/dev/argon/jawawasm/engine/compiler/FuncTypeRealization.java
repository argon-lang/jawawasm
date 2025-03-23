package dev.argon.jawawasm.engine.compiler;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.function.Supplier;

record FuncTypeRealization(
	ClassDesc classDesc,
	String methodName,
	Supplier<MethodTypeDesc> methodType
) implements DefTypeRealization {}
