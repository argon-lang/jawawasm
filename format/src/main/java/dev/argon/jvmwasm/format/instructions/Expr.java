package dev.argon.jvmwasm.format.instructions;

import java.util.List;

/**
 * An expression.
 * @param body The instructions.
 */
public record Expr(List<? extends Instr> body) {
}
