package dev.argon.jvmwasm.format.types;

import java.util.List;

/**
 * The result type for a function or expression.
 * @param types The result types.
 */
public record ResultType(List<? extends ValType> types) {
}
