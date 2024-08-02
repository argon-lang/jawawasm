package jvmwasm.format.instructions;

import java.util.List;

public record Expr(List<? extends Instr> body) {
}
