package dev.argon.jawawasm.format.modules;

import dev.argon.jawawasm.format.instructions.Expr;
import dev.argon.jawawasm.format.types.TableType;

/**
 * A table section.
 * @param type The type of the table.
 * @param init The initial value.
 */
public record Table(TableType type, Expr init) {
}
