package dev.argon.jawawasm.engine.compiler;

import java.lang.constant.ClassDesc;

class Constants {
	private Constants() {}

	public static final String RUNTIME_PACKAGE = "dev.argon.jawawasm.runtime";

	public static final ClassDesc memoryType = ClassDesc.of(RUNTIME_PACKAGE, "WasmMemory");
	public static final ClassDesc wasmModuleClass = ClassDesc.of(RUNTIME_PACKAGE, "WasmModule");
}
