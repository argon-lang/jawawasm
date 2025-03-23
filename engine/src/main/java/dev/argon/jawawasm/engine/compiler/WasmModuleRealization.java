package dev.argon.jawawasm.engine.compiler;

import com.google.common.collect.ImmutableList;

import java.lang.constant.ClassDesc;

/**
 * Information about the realization of a module.
 * @param classDesc The class that represents the module.
 * @param exports The realizations of the module's exports.
 */
public record WasmModuleRealization(
	ClassDesc classDesc,
	ImmutableList<WasmExportRealization> exports
) {
}
