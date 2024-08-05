package dev.argon.jvmwasm.format.modules;

/**
 * An import section.
 * @param module The module to import.
 * @param name The import name.
 * @param desc The import descriptor.
 */
public record Import(String module, String name, ImportDesc desc) {
}
