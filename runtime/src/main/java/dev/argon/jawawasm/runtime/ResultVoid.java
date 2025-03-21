package dev.argon.jawawasm.runtime;

/**
 * Result type with no values.
 */
public sealed interface ResultVoid extends WasmResult {

	/**
	 * Creates a void result.
	 * @return The void result.
	 */
	static ResultVoid of() {
		return new EndResult();
	}

	/**
	 * Gets the end result values, running the trampolines if needed.
	 * @param res The void result.
	 * @return The end result values.
	 */
	static ResultVoid.EndResult get(ResultVoid res) {
		for(;;) {
			switch(res) {
				case Step step -> res = step.step();
				case EndResult endResult -> {
					return endResult;
				}
			}
		}
	}

	/**
	 * A step value that must be executed to get the end result.
	 */
	non-sealed interface Step extends ResultVoid {
		/**
		 * Runs the execution step.
		 * @return The next void result.
		 */
		ResultVoid step();
	}

	/**
	 * A void end result.
	 */
	final class EndResult implements ResultVoid {
		EndResult() {}
	}
}
