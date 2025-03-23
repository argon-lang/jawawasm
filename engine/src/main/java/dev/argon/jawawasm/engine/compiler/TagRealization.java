package dev.argon.jawawasm.engine.compiler;

import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

record TagRealization(
	ClassDesc classDesc,
	InnerClassInfo innerClassInfo,
	MethodTypeDesc constructorType
) {
}
