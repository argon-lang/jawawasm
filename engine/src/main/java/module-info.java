/**
 * JVM WebAssembly engine
 */
module dev.argon.jawawasm.engine {
	requires transitive dev.argon.jawawasm.format;
	requires static org.jspecify;

	exports dev.argon.jawawasm.engine.validator;
	exports dev.argon.jawawasm.engine.internal;
	exports dev.argon.jawawasm.engine.interpreter;
}
