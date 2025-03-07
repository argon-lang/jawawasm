package dev.argon.jawawasm.engine;

/**
 * An `unreachable` instruction was reached.
 */
public class UnreachableTrap extends RuntimeException {
	/**
	 * Create an UnreachableException.
	 */
	public UnreachableTrap() {}
}
