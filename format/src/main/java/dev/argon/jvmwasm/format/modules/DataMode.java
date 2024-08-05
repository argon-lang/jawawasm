package dev.argon.jvmwasm.format.modules;

import dev.argon.jvmwasm.format.instructions.Expr;

/**
 * THe data mode.
 */
public sealed interface DataMode {
	/**
	 * A passive data.
	 */
	public static record Passive() implements DataMode {}

	/**
	 * An active data.
	 * @param memory The memory.
	 * @param offset The offset in the memory.
	 */
	public static record Active(MemIdx memory, Expr offset) implements DataMode {}
}
