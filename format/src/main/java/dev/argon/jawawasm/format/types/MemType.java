package dev.argon.jawawasm.format.types;

/**
 * The type of a memory.
 * @param addrType The address type.
 * @param limits The limits on the memory sizes.
 */
public record MemType(AddrType addrType, Limits limits) implements ExternalType {

	/**
	 * Represents an address type.
	 */
	public enum AddrType {
		/**
		 * Indicates the use of 32-bit addresses.
		 */
		I32 {
			@Override
			public NumType asNumType() {
				return NumType.I32;
			}

			@Override
			public long unboxAddress(Object address) {
				return Integer.toUnsignedLong((int)address);
			}

			@Override
			public Object boxAddress(long address) {
				return (int)address;
			}
		},

		/**
		 * Indicates the use of 64-bit addresses.
		 */
		I64 {
			@Override
			public NumType asNumType() {
				return NumType.I64;
			}

			@Override
			public long unboxAddress(Object address) {
				return (long)address;
			}

			@Override
			public Object boxAddress(long address) {
				return address;
			}
		},
		;

		/**
		 * Gets the NumType for this address type.
		 * @return The corresponding num type.
		 */
		public abstract NumType asNumType();

		/**
		 * Unbox an address as a long.
		 * @param address The boxed address.
		 * @return The address as a long.
		 */
		public abstract long unboxAddress(Object address);

		/**
		 * Box an address.
		 * @param address The long value of the address.
		 * @return The address boxed in the correct type.
		 */
		public abstract Object boxAddress(long address);
	}

}
