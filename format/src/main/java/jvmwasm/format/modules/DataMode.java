package jvmwasm.format.modules;

import jvmwasm.format.instructions.Expr;

public sealed interface DataMode {
	public static record Passive() implements DataMode {}

	public static record Active(MemIdx memory, Expr offset) implements DataMode {}
}
