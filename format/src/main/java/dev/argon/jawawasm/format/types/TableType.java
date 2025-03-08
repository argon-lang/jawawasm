package dev.argon.jawawasm.format.types;

/**
 * Table type
 * @param addrType The address type.
 * @param limits Table size limits.
 * @param elementType Table element type.
 */
public record TableType(MemType.AddrType addrType, Limits limits, RefType elementType) implements ExternalType {
}
