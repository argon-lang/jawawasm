package dev.argon.jawawasm.format.types;

import dev.argon.jawawasm.runtime.AddrType;

/**
 * The type of a memory.
 * @param addrType The address type.
 * @param limits The limits on the memory sizes.
 */
public record MemType(AddrType addrType, Limits limits) implements ExternalType {

}
