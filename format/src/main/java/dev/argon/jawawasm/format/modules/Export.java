package dev.argon.jawawasm.format.modules;

/**
 * Export section.
 * @param name The name of the export.
 * @param desc The export descriptor.
 */
public record Export(String name, ExportDesc desc) {
}
