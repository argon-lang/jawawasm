package dev.argon.jawawasm.engine.compiler;

import com.google.common.collect.ImmutableList;

import java.lang.classfile.MethodSignature;
import java.lang.classfile.TypeAnnotation;
import java.lang.constant.MethodTypeDesc;

/**
 * The realization of a method type.
 * @param descriptor The method descriptor.
 * @param methodSignature The method signature.
 * @param resultType The realization of the result type returned by this function.
 * @param parameterTypes The realization of the parameter types of this method.
 * @param methodTypeAnnotations Type annotations for the method signature.
 */
public record MethodTypeRealization(
	MethodTypeDesc descriptor,
	MethodSignature methodSignature,
	ResultTypeRealization resultType,
	ImmutableList<TypeRealization> parameterTypes,
	ImmutableList<TypeAnnotation> methodTypeAnnotations
) {
}
