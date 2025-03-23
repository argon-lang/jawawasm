package dev.argon.jawawasm.engine.interpreter;

import com.google.common.collect.ImmutableList;
import dev.argon.jawawasm.format.types.*;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A WebAssembly function.
 */
public non-sealed interface DynamicWasmFunction extends WasmExport, DynamicWasmObject {
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
	DynamicFunctionResult invoke(@Nullable Object[] args) throws Throwable;

	/**
	 * Invoke the function
	 * @param args The function arguments.
	 * @return The return vaules.
	 * @throws Throwable if an error occurs.
	 */
	default @Nullable Object[] invokeNow(@Nullable Object[] args) throws Throwable {
		return DynamicFunctionResult.resolve(invoke(args));
	}

	/**
	 * A simple function that constructs its deftype from the function type.
	 */
	public static interface SimpleFunction extends DynamicWasmFunction {
		@Override
		default DefType type() {
			return new DefType(
				new RecursiveType(ImmutableList.of(
					new SubType(
						true,
						ImmutableList.of(),
						functionType()
					)
				)),
				0
			);
		}
	}
}
