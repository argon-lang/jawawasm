package dev.argon.jawawasm.engine.interpreter;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * The result of a function call.
 */
public sealed interface DynamicFunctionResult {
	/**
	 * Values were returned from the function.
	 */
	public static final class Values implements DynamicFunctionResult {
		private final @Nullable Object[] values;

		/**
		 * Create a values result.
		 * @param values The return values.
		 */
		public Values(@Nullable Object[] values) {
			this.values = values;
		}

		/**
		 * Get the values.
		 * @return The values.
		 */
		public @Nullable Object[] values() {
			return values;
		}
	}

	/**
	 * The delayed result of a function call.
	 */
	public static non-sealed interface Delay extends DynamicFunctionResult {
		/**
		 * Continue execution of the function.
		 * @return The function result.
		 * @throws Throwable if an error occurs.
		 */
		DynamicFunctionResult step() throws Throwable;
	}


	/**
	 * Resolve a function result to get the return value.
	 * @param result The function result.
	 * @return The return values.
	 * @throws Throwable if an error occurs during continued evaluation.
	 */
	public static @Nullable Object[] resolve(DynamicFunctionResult result) throws Throwable {
		@Nullable Object[] value = null;
		while(value == null) {
			switch(result) {
				case Values values -> value = values.values();
				case Delay delay -> result = delay.step();
			}
		}
		return value;
	}

	/**
	 * Resolve a function result to get the return value.
	 * @param result The function result.
	 * @return The return values.
	 * @throws ExecutionException if an error occurs during evaluation.
	 */
	public static @Nullable Object[] resolveWith(DynamicFunctionResult.Delay result) throws ExecutionException {
		try {
			return resolve(result);
		}
		catch(Throwable ex) {
			throw new ExecutionException(ex);
		}
	}

}
