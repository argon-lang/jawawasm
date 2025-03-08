package dev.argon.jawawasm.format.types;

/**
 * A type of an import or export.
 */
public sealed interface ExternalType permits DefType, TableType, MemType, GlobalType, TagType {
}
