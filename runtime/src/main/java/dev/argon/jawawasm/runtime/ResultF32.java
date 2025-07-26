package dev.argon.jawawasm.runtime;

/**
 * Result type with no values.
 */
public sealed interface ResultF32 extends WasmResult {

	/**
	 * Creates a f32 result.
	 * @param item0 The item of this result.
	 * @return The f32 result.
	 */
	static ResultF32 of(float item0) {
		return new EndResult(item0);
	}

	/**
	 * Gets the end result values, running the trampolines if needed.
	 * @param res The f32 result.
	 * @return The end result values.
	 */
	static ResultF32.EndResult get(ResultF32 res) {
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
	non-sealed interface Step extends ResultF32 {
		/**
		 * Runs the execution step.
		 * @return The next f32 result.
		 */
		ResultF32 step();
	}

	/**
	 * A f32 end result.
	 */
	final class EndResult implements ResultF32 {
		EndResult(float item0) {
			this.item0 = item0;
		}

		/**
		 * The item of this result.
		 */
		public final float item0;
	}
}
