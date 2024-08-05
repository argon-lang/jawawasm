package dev.argon.jvmwasm.format.types;

/**
 * A function type.
 * @param args The argument types.
 * @param results The result types.
 */
public record FuncType(ResultType args, ResultType results) {
}
