package dev.argon.jawawasm.format.instructions;

/**
 * The terminator of a block.
 */
public enum BlockTerminator implements InstrOrTerminator {
	/**
	 * The end of a block.
	 */
	END,
	/**
	 * Ends an if block and begins an else block.
	 */
	ELSE,
}
