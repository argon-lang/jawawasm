/**
 * JVM WebAssembly executable.
 */
module dev.argon.jawawasm.app {
	requires org.jspecify;
	requires dev.argon.jawawasm.format;
	requires dev.argon.jawawasm.engine;
	requires com.fasterxml.jackson.annotation;
	requires org.apache.commons.io;
	requires tools.jackson.databind;
	requires tools.jackson.datatype.guava;
}
