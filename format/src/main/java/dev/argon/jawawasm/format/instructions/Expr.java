package dev.argon.jawawasm.format.instructions;

import com.google.common.collect.ImmutableList;

/**
 * An expression.
 * @param body The instructions.
 */
public record Expr(ImmutableList<Instr> body) {
}
