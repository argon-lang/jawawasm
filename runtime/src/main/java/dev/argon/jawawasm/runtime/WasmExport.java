package dev.argon.jawawasm.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates a WebAssembly export within a module.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface WasmExport {

	/**
	 * Provides an alternate name for the export.
	 * @return The name of the export, or the empty string if the method name should be used.
	 */
	String rename() default "";

	/**
	 * Indicates that the name returned by `rename` should be used even if it is the empty string.
	 * @return true if `rename` should always be used as the export name.
	 */
	boolean allowEmptyName() default false;

	/**
	 * The type of the export.
	 * @return The export type.
	 */
	ExportType type();

	/**
	 * The type of export.
	 */
	enum ExportType {
		/**
		 * A function export.
		 */
		FUNC,

		/**
		 * A table export.
		 */
		TABLE,

		/**
		 * A global export
		 */
		GLOBAL,

		/**
		 * A memory export
		 */
		MEMORY,
	}
}
