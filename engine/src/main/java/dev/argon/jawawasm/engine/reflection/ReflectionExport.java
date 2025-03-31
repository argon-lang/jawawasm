package dev.argon.jawawasm.engine.reflection;

import java.lang.reflect.Method;

/**
 * Represents an export of a module instance accessed by reflection.
 */
public sealed interface ReflectionExport {
	/**
	 * A function export.
	 * @param method The method that exports the function.
	 */
	record FunctionExport(Method method) implements ReflectionExport {}

	/**
	 * A global export.
	 */
	sealed interface GlobalExport extends ReflectionExport {}

	/**
	 * A `const` global export.
	 * @param method A method that returns the global value.
	 */
	record GlobalExportConst(Method method) implements GlobalExport {}

	/**
	 * A `mut` global export.
	 * @param method A method that returns a `WasmGlobal` containing the global value.
	 */
	record GlobalExportVar(Method method) implements GlobalExport {}
}
