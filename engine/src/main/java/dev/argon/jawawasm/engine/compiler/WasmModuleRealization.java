package dev.argon.jawawasm.engine.compiler;

import java.lang.constant.ClassDesc;
import java.util.List;

/**
 * Information about the realization of a module.
 * @param classDesc The class that represents the module.
 * @param exports The realizations of the module's exports.
 */
public record WasmModuleRealization(
	ClassDesc classDesc,
	List<WasmExportRealization> exports
) {
}
