package dev.argon.jawawasm.engine.compiler;

import java.lang.constant.ClassDesc;

/**
 * A realization of a WebAssembly type in Java.
 * @param type The descriptor of the type.
 * @param isNullable Indicates if the type allows null values.
 */
public record TypeRealization(ClassDesc type, boolean isNullable) {}
