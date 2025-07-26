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

	/**
	 * A memory export.
	 * @param method The method that returns the memory.
	 */
	record MemoryExport(Method method) implements ReflectionExport {}

	/**
	 * A table export.
	 * @param method The method that returns the table.
	 */
	record TableExport(Method method) implements ReflectionExport {}

	/**
	 * A tag export.
	 * @param tagClass The exception class that represents the tag.
	 */
	record TagExport(Class<?> tagClass) implements ReflectionExport {}
}
