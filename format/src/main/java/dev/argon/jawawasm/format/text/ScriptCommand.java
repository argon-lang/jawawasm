package dev.argon.jawawasm.format.text;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A command in a wast script.
 */
public sealed interface ScriptCommand {
	/**
	 * Defines a module
	 * @param name The name of the module.
	 * @param expr The content of the module.
	 */
	public static record ScriptModule(@Nullable String name, SExpr expr) implements ScriptCommand {}

	/**
	 * Register a module as an import.
	 * @param importName The name of the import.
	 * @param name The name of the module.
	 */
	public static record Register(String importName, @Nullable String name) implements ScriptCommand {}

	/**
	 * An executable action.
	 */
	public static sealed interface Action extends ScriptCommand {
		/**
		 * Invoke a function.
		 * @param name The name of the module.
		 * @param export The name of the export.
		 * @param exprs The argument values.
		 */
		public static record Invoke(@Nullable String name, String export, List<? extends SExpr> exprs) implements Action {}

		/**
		 * Get an export value.
		 * @param name The name of the module.
		 * @param export The name of the export.
		 */
		public static record Get(@Nullable String name, String export) implements Action {}
	}

	/**
	 * An assertion command
	 */
	public static sealed interface Assertion extends ScriptCommand {
		/**
		 * Asserts that a value is returned.
		 * @param action The action.
		 * @param results The expected results.
		 */
		public static record AssertReturn(Action action, List<? extends SExpr> results) implements Assertion {}

		/**
		 * Asserts that a trap occurs
		 * @param action The action.
		 * @param failure The failure message.
		 */
		public static record AssertTrap(Action action, String failure) implements Assertion {}

		/**
		 * Asserts that resources are exhausted
		 * @param action The action.
		 * @param failure The failure message.
		 */
		public static record AssertExhaustion(Action action, String failure) implements Assertion {}

		/**
		 * Asserts that a module is malformed
		 * @param module The module.
		 * @param failure The failure message.
		 */
		public static record AssertMalformed(SExpr module, String failure) implements Assertion {}

		/**
		 * Asserts that a module fails validation
		 * @param module The module.
		 * @param failure The failure message.
		 */
		public static record AssertInvalid(SExpr module, String failure) implements Assertion {}

		/**
		 * Asserts that a module could not be linked
		 * @param module The module.
		 * @param failure The failure message.
		 */
		public static record AssertUnlinkable(SExpr module, String failure) implements Assertion {}

		/**
		 * Asserts that a trap occurs during instantiation.
		 * @param module The module.
		 * @param failure The failure message.
		 */
		public static record AssertTrapInstantiation(SExpr module, String failure) implements Assertion {}
	}
}
