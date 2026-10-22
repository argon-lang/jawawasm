package dev.argon.jawawasm.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Identifies a WebAssembly struct field or array element mutator. The method
 * returns {@code void} and takes the field value for a struct, or an
 * {@code int} element index followed by the value for an array.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FieldMutator {
	/** The zero-based struct field index, or zero for an array element. */
	int value();
}
