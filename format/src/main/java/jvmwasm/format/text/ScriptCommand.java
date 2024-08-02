package jvmwasm.format.text;

import org.jspecify.annotations.Nullable;

import java.util.List;

public sealed interface ScriptCommand {
	public static record ScriptModule(@Nullable String name, SExpr expr) implements ScriptCommand {}

	public static record Register(String importName, @Nullable String name) implements ScriptCommand {}

	public static sealed interface Action extends ScriptCommand {
		public static record Invoke(@Nullable String name, String export, List<? extends SExpr> exprs) implements Action {}
		public static record Get(@Nullable String name, String export) implements Action {}
	}

	public static sealed interface Assertion extends ScriptCommand {
		public static record AssertReturn(Action action, List<? extends SExpr> results) implements Assertion {}
		public static record AssertTrap(Action action, String failure) implements Assertion {}
		public static record AssertExhaustion(Action action, String failure) implements Assertion {}
		public static record AssertMalformed(SExpr module, String failure) implements Assertion {}
		public static record AssertInvalid(SExpr module, String failure) implements Assertion {}
		public static record AssertUnlinkable(SExpr module, String failure) implements Assertion {}
		public static record AssertTrapInstantiation(SExpr module, String failure) implements Assertion {}
	}
}
