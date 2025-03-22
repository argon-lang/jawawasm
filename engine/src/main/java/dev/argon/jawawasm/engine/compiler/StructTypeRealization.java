package dev.argon.jawawasm.engine.compiler;

import org.jspecify.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.List;

public record StructTypeRealization(
	ClassDesc classDesc,
	boolean isInterface,
	List<Field> fields
) implements DefTypeRealization {

	record Field(
		ClassDesc fieldType,
		AccessMethod getMethod,
		@Nullable AccessMethod setMethod
	) {}

	record AccessMethod(String methodName, MethodTypeDesc methodType) {}

}
