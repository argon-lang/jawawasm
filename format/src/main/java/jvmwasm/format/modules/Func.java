package jvmwasm.format.modules;

import jvmwasm.format.instructions.Expr;
import jvmwasm.format.types.ValType;

import java.util.List;

public record Func(
	TypeIdx type,
	List<? extends ValType> locals,
	Expr body
) {
}
