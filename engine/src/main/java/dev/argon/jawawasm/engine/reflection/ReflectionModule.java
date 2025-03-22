package dev.argon.jawawasm.engine.reflection;

import dev.argon.jawawasm.runtime.WasmModule;

import java.util.Map;

public record ReflectionModule(WasmModule module, Map<String, ReflectionExport> exports) {
}
