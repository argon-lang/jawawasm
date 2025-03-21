package dev.argon.jawawasm.engine.compiler;

import java.lang.constant.ClassDesc;

sealed interface DefTypeRealization permits FuncTypeRealization {
	ClassDesc classDesc();
}
