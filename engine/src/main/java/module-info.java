/**
 * JVM WebAssembly engine
 */
module dev.argon.jvmwasm.engine {
	requires transitive dev.argon.jvmwasm.format;
	requires org.jspecify;

	exports dev.argon.jvmwasm.engine;
	exports dev.argon.jvmwasm.engine.validator;
}
