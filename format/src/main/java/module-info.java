/**
 * WebAssembly Module format.
 */
module dev.argon.jawawasm.format {
	requires static org.jspecify;
	requires transitive dev.argon.jawawasm.runtime;
	requires transitive com.google.common;
	requires com.google.protobuf;
	exports dev.argon.jawawasm.format;
	exports dev.argon.jawawasm.format.binary;
	exports dev.argon.jawawasm.format.instructions;
	exports dev.argon.jawawasm.format.modules;
	exports dev.argon.jawawasm.format.types;
}
