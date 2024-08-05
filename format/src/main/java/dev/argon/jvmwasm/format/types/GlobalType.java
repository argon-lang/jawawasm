package dev.argon.jvmwasm.format.types;

/**
 * The type of a global.
 * @param mutability The mutability of the global.
 * @param type The type of the value.
 */
public record GlobalType(Mut mutability, ValType type) {
}
