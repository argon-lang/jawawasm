package dev.argon.jawawasm.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the supertypes and recursive group of a concrete WebAssembly type.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface WasmSubtype {
	/**
	 * The ordered declared WebAssembly supertypes.
	 *
	 * @return the ordered declared WebAssembly supertypes
	 */
	Class<?>[] supertypes() default {};

	/**
	 * The ordered recursive-group members; empty denotes a singleton containing
	 * this class.
	 *
	 * @return the ordered recursive-group members, or an empty array for a
	 *         singleton containing this class
	 */
	Class<?>[] recursiveGroup() default {};

	/**
	 * The zero-based position of this class in its recursive group.
	 *
	 * @return the zero-based position of this class in its recursive group
	 */
	int groupIndex() default 0;
}
