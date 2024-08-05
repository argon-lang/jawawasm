package dev.argon.jvmwasm.format.modules;

import dev.argon.jvmwasm.format.instructions.Expr;
import dev.argon.jvmwasm.format.types.RefType;

import java.util.List;

/**
 * An elem section.
 * @param type The element type.
 * @param init The initializer.
 * @param mode The element mode.
 */
public record Elem(RefType type, List<? extends Expr> init, ElemMode mode) {
}
