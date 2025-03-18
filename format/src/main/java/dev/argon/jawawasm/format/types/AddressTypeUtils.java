package dev.argon.jawawasm.format.types;

import dev.argon.jawawasm.runtime.AddrType;
import dev.argon.jawawasm.runtime.WasmMemory;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Utility methods for address types.
 */
public class AddressTypeUtils {
	private AddressTypeUtils() {}

	/**
	 * Gets the NumType for an address type.
	 *
	 * @param addrType The address type.
	 * @return The corresponding num type.
	 */
	public static NumType asNumType(AddrType addrType) {
		return switch(addrType) {
			case I32 -> NumType.I32;
			case I64 -> NumType.I64;
		};
	}


	/**
	 * Unbox an address as a long.
	 *
	 * @param addrType The address type
	 * @param address The boxed address.
	 * @return The address as a long.
	 */
	public static long unboxAddress(AddrType addrType, @Nullable Object address) {
		Objects.requireNonNull(address);

		return switch(addrType) {
			case I32 -> Integer.toUnsignedLong((int)address);
			case I64 -> (long)address;
		};
	}


	/**
	 * Box an address.
	 *
	 * @param addrType The address type
	 * @param address The long value of the address.
	 * @return The address boxed in the correct type.
	 */
	public static Object boxAddress(AddrType addrType, long address) {
		return switch(addrType) {
			case I32 -> (int)address;
			case I64 -> address;
		};
	}

	public static MemType getMemoryType(WasmMemory memory) {
		return new MemType(memory.addressType(), new Limits(memory.pageSize(), memory.maxPageSize()));
	}
}
