package dev.argon.jawawasm.engine.reflection;

import dev.argon.jawawasm.runtime.WasmModule;

import java.util.Map;

/**
 * A WebAssembly module accessed by reflection.
 * @param module The module.
 * @param exports The exports for the module.
 */
public record ReflectionModule(WasmModule module, Map<String, ReflectionExport> exports) {
}
