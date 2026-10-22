package dev.argon.jawawasm.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Identifies a WebAssembly function type inferred from the interface's SAM. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface WasmFuncType {
	/** Whether the WebAssembly function type is final. */
	boolean isFinal();
}
