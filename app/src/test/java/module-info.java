/**
 * Tests for the WebAssembly application.
 */
module dev.argon.jawawasm.app.tests {
	requires static org.jspecify;
	requires dev.argon.jawawasm.app;
	requires org.junit.jupiter.api;
	requires com.google.errorprone.annotations;

	exports dev.argon.jawawasm.app.tests;
}
