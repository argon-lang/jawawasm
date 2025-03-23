package dev.argon.jawawasm.format.types;

import com.google.common.collect.ImmutableList;

/**
 * The result type for a function or expression.
 * @param types The result types.
 */
public record ResultType(ImmutableList<ValType> types) {
}
