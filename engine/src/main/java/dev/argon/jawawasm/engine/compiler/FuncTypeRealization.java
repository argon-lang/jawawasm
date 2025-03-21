package dev.argon.jawawasm.engine.compiler;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

record FuncTypeRealization(
	ClassDesc classDesc,
	String methodName,
	MethodTypeDesc methodType
) implements DefTypeRealization {}
