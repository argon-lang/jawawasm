package dev.argon.jawawasm.engine.compiler;

import org.jspecify.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import java.util.function.Supplier;

public record StructTypeRealization(
	ClassDesc classDesc,
	boolean isInterface,
	String createMethodName,
	Supplier<MethodTypeDesc> createMethodType,
	List<Field> fields,
	Supplier<@Nullable StructTypeRealization> superType
) implements DefTypeRealization {

	record Field(
		Supplier<ClassDesc> fieldType,
		AccessMethod getMethod,
		@Nullable AccessMethod setMethod
	) {}

	record AccessMethod(String methodName, Supplier<MethodTypeDesc> methodType) {}

}
