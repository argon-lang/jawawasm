package dev.argon.jvmwasm.engine;

/**
 * The result of a function call.
 */
public sealed interface FunctionResult {
	/**
	 * Values were returned from the function.
	 * @param values The return values.
	 */
	public static record Values(Object[] values) implements FunctionResult {}

	/**
	 * The delayed result of a function call.
	 */
	public static non-sealed interface Delay extends FunctionResult {
		/**
		 * Continue execution of the function.
		 * @return The function result.
		 * @throws Throwable if an error occurs.
		 */
		FunctionResult step() throws Throwable;
	}


	/**
	 * Resolve a function result to get the return value.
	 * @param result The function result.
	 * @return The return values.
	 * @throws Throwable if an error occurs during continued evaluation.
	 */
	public static Object[] resolve(FunctionResult result) throws Throwable {
		Object[] value = null;
		while(value == null) {
			switch(result) {
				case Values values -> value = values.values();
				case Delay delay -> result = delay.step();
			}
		}
		return value;
	}

}
