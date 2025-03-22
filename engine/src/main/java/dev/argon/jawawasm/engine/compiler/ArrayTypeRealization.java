package dev.argon.jawawasm.engine.compiler;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

public record ArrayTypeRealization(
	ClassDesc classDesc,
	boolean isInterface,
	ClassDesc elementType
) implements DefTypeRealization {
}
