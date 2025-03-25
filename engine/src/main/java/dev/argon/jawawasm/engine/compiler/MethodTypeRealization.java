package dev.argon.jawawasm.engine.compiler;

import com.google.common.collect.ImmutableList;

import java.lang.classfile.MethodSignature;
import java.lang.constant.MethodTypeDesc;

public record MethodTypeRealization(
	MethodTypeDesc descriptor,
	MethodSignature methodSignature,
	ResultTypeRealization resultType,
	ImmutableList<TypeRealization> parameterTypes
) {
}
