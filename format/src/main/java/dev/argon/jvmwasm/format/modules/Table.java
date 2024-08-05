package dev.argon.jvmwasm.format.modules;

import dev.argon.jvmwasm.format.types.TableType;

/**
 * A table section.
 * @param type The type of the table.
 */
public record Table(TableType type) {
}
