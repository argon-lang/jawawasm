package dev.argon.jawawasm.format.modules;

import dev.argon.jawawasm.format.types.MemType;
import dev.argon.jawawasm.format.types.GlobalType;
import dev.argon.jawawasm.format.types.TableType;

/**
 * An import descriptor.
 */
public sealed interface ImportDesc {
	/**
	 * A function import.
	 * @param type The function type.
	 */
	public static record Func(TypeIdx type) implements ImportDesc {}

	/**
	 * A table import.
	 * @param type The table type.
	 */
	public static record Table(TableType type) implements ImportDesc {}

	/**
	 * A memory import.
	 * @param type The memory type.
	 */
	public static record Mem(MemType type) implements ImportDesc {}

	/**
	 * A global import.
	 * @param type The global type.
	 */
	public static record Global(GlobalType type) implements ImportDesc {}
}
