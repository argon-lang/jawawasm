package dev.argon.jvmwasm.format.modules;

import dev.argon.jvmwasm.format.instructions.Expr;

/**
 * The elem mode.
 */
public sealed interface ElemMode {

	/**
	 * A passive elem.
	 */
	public static record Passive() implements ElemMode {}

	/**
	 * An active elem.
	 * @param table The table.
	 * @param offset The offset into the table.
	 */
	public static record Active(TableIdx table, Expr offset) implements ElemMode {}

	/**
	 * A declarative elem.
	 */
	public static record Declarative() implements ElemMode {}

}
