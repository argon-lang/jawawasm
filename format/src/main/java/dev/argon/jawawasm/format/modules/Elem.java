package dev.argon.jawawasm.format.modules;

import dev.argon.jawawasm.format.instructions.Expr;
import dev.argon.jawawasm.format.types.RefType;

import java.util.List;

/**
 * An elem section.
 * @param type The element type.
 * @param init The initializer.
 * @param mode The element mode.
 */
public record Elem(RefType type, List<? extends Expr> init, ElemMode mode) {
}
