package dev.argon.jawawasm.format.modules;

import dev.argon.jawawasm.format.instructions.Expr;
import dev.argon.jawawasm.format.types.GlobalType;

/**
 * A global section.
 * @param type The type of the global.
 * @param init The initial value of the global.
 */
public record Global(GlobalType type, Expr init) {
}
