package dev.argon.jawawasm.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines size limits for tables and memory.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
public @interface SizeLimits {
	/**
	 * The type of the address.
	 * @return The address type.
	 */
	AddrType addressType();

	/**
	 * The minimum size allowed.
	 * @return The minimum size.
	 */
	long min();

	/**
	 * The maximum size allowed or -1 if unlimited.
	 * @return The maximum size.
	 */
	long max() default -1;
}
