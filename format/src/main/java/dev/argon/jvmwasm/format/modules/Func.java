package dev.argon.jvmwasm.format.modules;

import dev.argon.jvmwasm.format.instructions.Expr;
import dev.argon.jvmwasm.format.types.ValType;

import java.util.List;

/**
 * A function.
 * @param type The function type index.
 * @param locals The local variables.
 * @param body The function body.
 */
public record Func(
	TypeIdx type,
	List<? extends ValType> locals,
	Expr body
) {
}
