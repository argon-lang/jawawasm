package dev.argon.jawawasm.format.types;

import dev.argon.jawawasm.runtime.AddrType;

/**
 * Table type
 * @param addrType The address type.
 * @param limits Table size limits.
 * @param elementType Table element type.
 */
public record TableType(AddrType addrType, Limits limits, RefType elementType) implements ExternalType {
}
