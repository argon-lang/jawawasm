package dev.argon.jvmwasm.engine;

import dev.argon.jvmwasm.format.types.FuncType;

import java.util.concurrent.ExecutionException;

/**
 * A WebAssembly function.
 */
public non-sealed interface WasmFunction extends WasmExport {
	/**
	 * Gets the function type.
	 * @return The function type.
	 */
	FuncType type();

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
	 * Checks that the function type matches.
	 * @param t The function type.
	 * @throws IndirectCallTypeMismatchException if the function type does not match.
	 */
	default void checkType(FuncType t) throws IndirectCallTypeMismatchException {
		if(!t.equals(type())) {
			throw new IndirectCallTypeMismatchException();
		}
	}

}
