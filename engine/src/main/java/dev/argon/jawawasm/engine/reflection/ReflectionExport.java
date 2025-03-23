package dev.argon.jawawasm.engine.reflection;

import java.lang.reflect.Method;

public sealed interface ReflectionExport {
	record FunctionExport(Method method) implements ReflectionExport {}
	sealed interface GlobalExport extends ReflectionExport {}
	record GlobalExportConst(Method method) implements GlobalExport {}
	record GlobalExportVar(Method method) implements GlobalExport {}
}
