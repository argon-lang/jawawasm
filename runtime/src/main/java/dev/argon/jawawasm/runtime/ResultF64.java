package dev.argon.jawawasm.runtime;

/**
 * Result type with no values.
 */
public sealed interface ResultF64 extends WasmResult {

	/**
	 * Creates a f64 result.
	 * @param item0 The item of this result.
	 * @return The f64 result.
	 */
	static ResultF64 of(double item0) {
		return new EndResult(item0);
	}

	/**
	 * Gets the end result values, running the trampolines if needed.
	 * @param res The f64 result.
	 * @return The end result values.
	 */
	static ResultF64.EndResult get(ResultF64 res) {
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
	non-sealed interface Step extends ResultF64 {
		/**
		 * Runs the execution step.
		 * @return The next f64 result.
		 */
		ResultF64 step();
	}

	/**
	 * A f64 end result.
	 */
	final class EndResult implements ResultF64 {
		EndResult(double item0) {
			this.item0 = item0;
		}

		/**
		 * The item of this result.
		 */
		public final double item0;
	}
}
