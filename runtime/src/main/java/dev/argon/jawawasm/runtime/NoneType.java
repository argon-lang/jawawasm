package dev.argon.jawawasm.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that this represents the empty type.
 * Maps ANY to NONE, EXTERN to NOEXTERN, EXN to NOEXN, and FUNC to NOFUNC.
 */
@Target(ElementType.TYPE_USE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NoneType {
}
