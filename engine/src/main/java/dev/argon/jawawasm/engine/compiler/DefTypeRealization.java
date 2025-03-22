package dev.argon.jawawasm.engine.compiler;

import java.lang.constant.ClassDesc;

sealed interface DefTypeRealization permits ArrayTypeRealization, FuncTypeRealization, StructTypeRealization {
	ClassDesc classDesc();
}
