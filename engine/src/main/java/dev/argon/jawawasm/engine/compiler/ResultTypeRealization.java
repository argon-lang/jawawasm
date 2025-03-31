package dev.argon.jawawasm.engine.compiler;

import com.google.common.collect.ImmutableList;

import java.lang.classfile.Signature;
import java.lang.constant.ClassDesc;

/**
 * The realization of a result type.
 * @param classDesc The descriptor representing the result type.
 * @param signature The signature representing the result type.
 * @param elementTypes The realization of the element types.
 * @param typeArguments The realization of the types of the arguments in the signature.
 */
public record ResultTypeRealization(
	ClassDesc classDesc,
	Signature signature,
	ImmutableList<TypeRealization> elementTypes,
	ImmutableList<TypeRealization> typeArguments
) {

}
