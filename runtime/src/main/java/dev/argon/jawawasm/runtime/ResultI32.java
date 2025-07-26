package dev.argon.jawawasm.runtime;

/**
 * Result type with no values.
 */
public sealed interface ResultI32 extends WasmResult {

	/**
	 * {@return The i32 result.}
	 * @param item0 The item of this result.
	 */
	static ResultI32 of(int item0) {
		return new EndResult(item0);
	}

	/**
	 * Gets the end result values, running the trampolines if needed.
	 * @param res The i32 result.
	 * @return The end result values.
	 */
	static ResultI32.EndResult get(ResultI32 res) {
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
	non-sealed interface Step extends ResultI32 {
		/**
		 * Runs the execution step.
		 * @return The next i32 result.
		 */
		ResultI32 step();
	}

	/**
	 * A i32 end result.
	 */
	final class EndResult implements ResultI32 {
		EndResult(int item0) {
			this.item0 = item0;
		}

		/**
		 * The item of this result.
		 */
		public final int item0;
	}
}
