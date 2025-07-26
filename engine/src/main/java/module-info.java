/**
 * JVM WebAssembly engine
 */
module dev.argon.jawawasm.engine {
	requires transitive dev.argon.jawawasm.format;
	requires transitive org.jspecify;
	requires com.google.protobuf;
	requires org.apache.commons.compress;
	requires jdk.compiler;
	requires org.apache.commons.io;
	requires com.google.errorprone.annotations;

	exports dev.argon.jawawasm.engine;
	exports dev.argon.jawawasm.engine.validator;
	exports dev.argon.jawawasm.engine.interpreter;
	exports dev.argon.jawawasm.engine.compiler;
	exports dev.argon.jawawasm.engine.reflection;
	exports dev.argon.jawawasm.engine.internal;
}
