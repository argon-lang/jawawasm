package dev.argon.jvmwasm.format.types;

/**
 * Reference type
 */
public sealed interface RefType extends ValType permits FuncRef, ExternRef {
}
