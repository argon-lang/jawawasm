/**
 * Tests for the WebAssembly engine.
 */
module dev.argon.jawawasm.engine.tests {
    requires static org.jspecify;
	requires dev.argon.jawawasm.engine;
	requires org.junit.jupiter.api;

	exports dev.argon.jawawasm.engine.tests.compiler;
	exports dev.argon.jawawasm.engine.tests.internal;
}
