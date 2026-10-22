package dev.argon.jawawasm.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.Repeatable;

/** Declares a tag export on a module, including aliases and imported tags. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(WasmTagExports.class)
public @interface WasmTagExport {
	/** The exact WebAssembly export name, which may be empty. */
	String name();

	/** The exception class that preserves this tag's identity. */
	Class<? extends WebAssemblyException> exception();

	/** The annotated function interface describing the tag parameters. */
	Class<?> functionType();
}
