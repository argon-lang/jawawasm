package dev.argon.jawawasm.format.types;

/**
 * Reference type
 */
public sealed interface RefType extends ValType permits FuncRef, ExternRef {
}
