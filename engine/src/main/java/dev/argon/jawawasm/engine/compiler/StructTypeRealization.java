package dev.argon.jawawasm.engine.compiler;

import org.jspecify.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import java.util.function.Supplier;

/**
 * The realization of a struct type.
 * @param classDesc The descriptor of the struct type.
 * @param isInterface Indicates whether the struct was realized as an interface.
 * @param createMethodName The name of the create method.
 * @param createMethodType The descriptor of the create method.
 * @param fields The field realizations.
 * @param superType The realization of the supertype, or null if no supertype.
 */
public record StructTypeRealization(
	ClassDesc classDesc,
	boolean isInterface,
	String createMethodName,
	Supplier<MethodTypeDesc> createMethodType,
	List<Field> fields,
	Supplier<@Nullable StructTypeRealization> superType
) implements DefTypeRealization {

	/**
	 * The realization of a field.
	 * @param fieldType The descriptor of the field.
	 * @param getMethod The get accessor.
	 * @param setMethod The set accessor, or null if the field is not mutable.
	 */
	record Field(
		Supplier<ClassDesc> fieldType,
		AccessMethod getMethod,
		@Nullable AccessMethod setMethod
	) {}

	/**
	 * The realization of an accessor method.
	 * @param methodName The name of the method.
	 * @param methodType The descriptor of the method.
	 */
	record AccessMethod(String methodName, Supplier<MethodTypeDesc> methodType) {}

}
