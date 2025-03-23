package dev.argon.jawawasm.engine.compiler;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;

/**
 * The compiler options.
 * @param javaPackage The java package where classes will be generated.
 */
public record CompilerOptions(
	ClassHierarchyResolver classHierarchyResolver,
	String javaPackage
) {
}
