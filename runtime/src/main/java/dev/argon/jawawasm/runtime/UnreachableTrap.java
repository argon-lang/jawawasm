package dev.argon.jawawasm.runtime;

/**
 * An `unreachable` instruction was reached.
 */
public class UnreachableTrap extends RuntimeException {
	/**
	 * Create an UnreachableException.
	 */
	public UnreachableTrap() {}
}
