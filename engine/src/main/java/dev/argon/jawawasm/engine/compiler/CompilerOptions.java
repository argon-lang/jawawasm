package dev.argon.jawawasm.engine.compiler;

import java.lang.classfile.ClassFile;

public record CompilerOptions(
	ClassFile classFile,
	String javaPackage
) {
}
