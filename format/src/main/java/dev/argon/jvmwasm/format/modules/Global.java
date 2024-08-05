package dev.argon.jvmwasm.format.modules;

import dev.argon.jvmwasm.format.instructions.Expr;
import dev.argon.jvmwasm.format.types.GlobalType;

/**
 * A global section.
 * @param type The type of the global.
 * @param init The initial value of the global.
 */
public record Global(GlobalType type, Expr init) {
}
