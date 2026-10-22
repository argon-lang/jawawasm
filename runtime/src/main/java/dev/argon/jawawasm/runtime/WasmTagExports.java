package dev.argon.jawawasm.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Contains the explicit tag exports of a module. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface WasmTagExports {
	/** The tag exports; an empty array declares no exported tags. */
	WasmTagExport[] value();
}
