package dev.argon.jawawasm.engine;

import dev.argon.jawawasm.format.types.*;

import java.util.List;

/**
 * A WebAssembly function.
 */
public non-sealed interface WasmFunction extends WasmExport, WasmObject {
	/**
	 * Gets the defined function type.
	 * @return The defined function type.
	 */
	DefType type();

	/**
	 * Gets the function type.
	 * @return The defined function type.
	 */
	FuncType functionType();

	@Override
	default HeapType heapType() {
		return type();
	}

	/**
	 * Invoke the function.
	 * @param args The function arguments.
	 * @return The function result.
	 * @throws Throwable if an error occurs.
	 */
	FunctionResult invoke(Object[] args) throws Throwable;

	/**
	 * Invoke the function
	 * @param args The function arguments.
	 * @return The return vaules.
	 * @throws Throwable if an error occurs.
	 */
	default Object[] invokeNow(Object[] args) throws Throwable {
		return FunctionResult.resolve(invoke(args));
	}

	/**
	 * A simple function that constructs its deftype from the function type.
	 */
	public static interface SimpleFunction extends WasmFunction {
		@Override
		default DefType type() {
			return new DefType(
				new RecursiveType(List.of(
					new SubType(
						true,
						List.of(),
						functionType()
					)
				)),
				0
			);
		}
	}
}
