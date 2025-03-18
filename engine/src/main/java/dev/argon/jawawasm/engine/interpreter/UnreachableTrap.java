package dev.argon.jawawasm.engine.interpreter;

/**
 * An `unreachable` instruction was reached.
 */
public class UnreachableTrap extends RuntimeException {
	/**
	 * Create an UnreachableException.
	 */
	public UnreachableTrap() {}
}
