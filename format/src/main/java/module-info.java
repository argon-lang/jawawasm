/**
 * WebAssembly format readers.
 */
module dev.argon.jawawasm.format {
	requires static org.jspecify;
	requires transitive dev.argon.jawawasm.format.core;
	requires transitive com.fasterxml.jackson.annotation;
	requires tools.jackson.databind;
	requires tools.jackson.datatype.guava;
	exports dev.argon.jawawasm.format.text;
	exports dev.argon.jawawasm.format.wast;
}
