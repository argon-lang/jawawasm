package dev.argon.jawawasm.engine.compiler;

import dev.argon.jawawasm.format.types.ExternalType;

import java.lang.constant.MethodTypeDesc;

/**
 * Information about the realization of an export.
 * @param exportName The export's name.
 * @param methodName The name of the method implementing the export.
 * @param methodType The descriptor of the method implementing the export.
 * @param externalType The type of the export.
 */
public record WasmExportRealization(
	String exportName,
	String methodName,
	MethodTypeDesc methodType,
	ExternalType externalType
) {
}
