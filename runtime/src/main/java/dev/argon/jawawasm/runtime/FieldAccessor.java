package dev.argon.jawawasm.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Identifies a WebAssembly struct field or array element accessor. The method
 * returns the field value and takes no parameters for a struct, or one
 * {@code int} element index for an array.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FieldAccessor {
	/** The zero-based struct field index, or zero for an array element. */
	int value();
}
