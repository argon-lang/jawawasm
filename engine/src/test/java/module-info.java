/**
 * Tests for the WebAssembly engine.
 */
module dev.argon.jawawasm.engine.tests {
	requires dev.argon.jawawasm.engine;
	requires org.junit.jupiter.api;

	opens dev.argon.jawawasm.engine.tests.internal to org.junit.platform.commons;
}
