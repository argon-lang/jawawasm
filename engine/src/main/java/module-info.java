/**
 * JVM WebAssembly engine
 */
module dev.argon.jawawasm.engine {
	requires transitive dev.argon.jawawasm.format;
	requires transitive org.jspecify;

	exports dev.argon.jawawasm.engine;
	exports dev.argon.jawawasm.engine.validator;
	exports dev.argon.jawawasm.engine.interpreter;
	exports dev.argon.jawawasm.engine.compiler;
	exports dev.argon.jawawasm.engine.classloader;
}
