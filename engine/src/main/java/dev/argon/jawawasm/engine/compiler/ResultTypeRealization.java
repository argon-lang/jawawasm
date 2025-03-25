package dev.argon.jawawasm.engine.compiler;

import com.google.common.collect.ImmutableList;

import java.lang.classfile.Signature;
import java.lang.classfile.TypeAnnotation;
import java.lang.constant.ClassDesc;

public record ResultTypeRealization(
	ClassDesc classDesc,
	Signature signature,
	ImmutableList<TypeRealization> elementTypes,
	ImmutableList<TypeRealization> typeArguments
) {

}
