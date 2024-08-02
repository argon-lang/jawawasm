package jvmwasm.format.modules;

import jvmwasm.format.instructions.Expr;

public sealed interface ElemMode {
	public static record Passive() implements ElemMode {}

	public static record Active(TableIdx table, Expr offset) implements ElemMode {}

	public static record Declarative() implements ElemMode {}

}
