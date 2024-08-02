package jvmwasm.format.modules;

import jvmwasm.format.instructions.Expr;
import jvmwasm.format.types.GlobalType;

public record Global(GlobalType type, Expr init) {
}
