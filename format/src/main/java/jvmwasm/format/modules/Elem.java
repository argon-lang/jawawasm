package jvmwasm.format.modules;

import jvmwasm.format.instructions.Expr;
import jvmwasm.format.types.RefType;

import java.util.List;

public record Elem(RefType type, List<? extends Expr> init, ElemMode mode) {
}
