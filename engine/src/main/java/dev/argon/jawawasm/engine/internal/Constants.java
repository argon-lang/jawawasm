package dev.argon.jawawasm.engine.internal;

import java.lang.constant.ClassDesc;

/**
 * Internal use only.
 * @hidden
 */
public class Constants {
	private Constants() {}

	public static final String RUNTIME_PACKAGE = "dev.argon.jawawasm.runtime";

	public static final ClassDesc wasmMemoryClass = ClassDesc.of(RUNTIME_PACKAGE, "WasmMemory");
	public static final ClassDesc i31Class = ClassDesc.of(RUNTIME_PACKAGE, "I31");
	public static final ClassDesc v128Class = ClassDesc.of(RUNTIME_PACKAGE, "WasmMemory");
	public static final ClassDesc wasmEqClass = ClassDesc.of(RUNTIME_PACKAGE, "WasmEq");
	public static final ClassDesc wasmStructClass = ClassDesc.of(RUNTIME_PACKAGE, "WasmStruct");
	public static final ClassDesc wasmArrayClass = ClassDesc.of(RUNTIME_PACKAGE, "WasmArray");
	public static final ClassDesc wasmModuleClass = ClassDesc.of(RUNTIME_PACKAGE, "WasmModule");
	public static final ClassDesc wasmTableClass = ClassDesc.of(RUNTIME_PACKAGE, "WasmTable");
	public static final ClassDesc wasmResultClass = ClassDesc.of(RUNTIME_PACKAGE, "WasmResult");
	public static final ClassDesc webAssemblyExceptionClass = ClassDesc.of(RUNTIME_PACKAGE, "WebAssemblyException");
	public static final ClassDesc wasmGlobalRefClass = ClassDesc.of(RUNTIME_PACKAGE, "GlobalRef");
	public static final ClassDesc wasmGlobalI32Class = ClassDesc.of(RUNTIME_PACKAGE, "GlobalI32");
	public static final ClassDesc wasmGlobalI64Class = ClassDesc.of(RUNTIME_PACKAGE, "GlobalI64");
	public static final ClassDesc wasmGlobalF32Class = ClassDesc.of(RUNTIME_PACKAGE, "GlobalF32");
	public static final ClassDesc wasmGlobalF64Class = ClassDesc.of(RUNTIME_PACKAGE, "GlobalF64");
	public static final ClassDesc wasmFunctionClass = ClassDesc.of(RUNTIME_PACKAGE, "WasmFunction");

	public static final ClassDesc wasmExportAnn = ClassDesc.of(RUNTIME_PACKAGE, "WasmExport");
	public static final ClassDesc sizeLimitsAnn = ClassDesc.of(RUNTIME_PACKAGE, "SizeLimits");
}
