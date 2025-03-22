package dev.argon.jawawasm.engine.reflection;

import java.lang.reflect.Method;

public sealed interface ReflectionExport {
	record FunctionExport(Method method) implements ReflectionExport {}
}
