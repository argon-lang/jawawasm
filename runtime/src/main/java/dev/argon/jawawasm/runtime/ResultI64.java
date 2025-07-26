package dev.argon.jawawasm.runtime;

/**
 * Result type with no values.
 */
public sealed interface ResultI64 extends WasmResult {

	/**
	 * Creates a i64 result.
	 * @param item0 The item of this result.
	 * @return The i64 result.
	 */
	static ResultI64 of(long item0) {
		return new EndResult(item0);
	}

	/**
	 * Gets the end result values, running the trampolines if needed.
	 * @param res The i64 result.
	 * @return The end result values.
	 */
	static ResultI64.EndResult get(ResultI64 res) {
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
	non-sealed interface Step extends ResultI64 {
		/**
		 * Runs the execution step.
		 * @return The next i64 result.
		 */
		ResultI64 step();
	}

	/**
	 * A i64 end result.
	 */
	final class EndResult implements ResultI64 {
		EndResult(long item0) {
			this.item0 = item0;
		}

		/**
		 * The item of this result.
		 */
		public final long item0;
	}
}
