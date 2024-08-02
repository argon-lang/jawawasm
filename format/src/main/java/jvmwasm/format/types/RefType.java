package jvmwasm.format.types;

public sealed interface RefType extends ValType permits FuncRef, ExternRef {
}
