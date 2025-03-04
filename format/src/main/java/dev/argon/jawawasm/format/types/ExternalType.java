package dev.argon.jawawasm.format.types;

public sealed interface ExternalType permits FuncType, TableType, MemType, GlobalType, TagType {
}
