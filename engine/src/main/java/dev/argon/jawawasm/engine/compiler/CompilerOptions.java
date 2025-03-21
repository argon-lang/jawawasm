package dev.argon.jawawasm.engine.compiler;

import java.lang.classfile.ClassFile;

/**
 * The compiler options.
 * @param classFile The ClassFile used to generate classes.
 * @param javaPackage The java package where classes will be generated.
 */
public record CompilerOptions(
	ClassFile classFile,
	String javaPackage
) {
}
