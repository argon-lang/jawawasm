package dev.argon.jawawasm.format.types;

public sealed interface ExternalType permits DefType, TableType, MemType, GlobalType, TagType {
}
