package dev.argon.jawawasm.runtime;

/**
 * Result type with no values.
 * @param <T> The type for item0.
 */
public sealed interface ResultRef<T> extends WasmResult {

	/**
	 * Creates a ref result.
	 * @param <T> The type for item0.
	 * @param item0 The item of this result.
	 * @return The ref result.
	 */
	static <T> ResultRef<T> of(T item0) {
		return new EndResult<T>(item0);
	}

	/**
	 * Gets the end result values, running the trampolines if needed.
	 * @param <T> The type for item0.
	 * @param res The ref result.
	 * @return The end result values.
	 */
	static <T> ResultRef.EndResult<T> get(ResultRef<T> res) {
		for(;;) {
			switch(res) {
				case Step<T> step -> res = step.step();
				case EndResult<T> endResult -> {
					return endResult;
				}
			}
		}
	}

	/**
	 * A step value that must be executed to get the end result.
	 * @param <T> The type for item0.
	 */
	non-sealed interface Step<T> extends ResultRef<T> {
		/**
		 * Runs the execution step.
		 * @return The next ref result.
		 */
		ResultRef<T> step();
	}

	/**
	 * A ref end result.
	 * @param <T> The type for item0.
	 */
	final class EndResult<T> implements ResultRef<T> {
		EndResult(T item0) {
			this.item0 = item0;
		}

		/**
		 * The item of this result.
		 */
		public final T item0;
	}
}
