package dev.argon.jawawasm.engine.compiler;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;

/**
 * The compiler options.
 * @param classHierarchyResolver Class hierarchy resolver to use to resolve preexisting classes (JDK, runtime library, etc.).
 * @param javaPackage The java package where classes will be generated.
 */
public record CompilerOptions(
	ClassHierarchyResolver classHierarchyResolver,
	String javaPackage
) {
}
