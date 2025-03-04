package dev.argon.jawawasm.format.types;

/**
 * The type of a memory.
 * @param limits The limits on the memory sizes.
 */
public record MemType(AddrType addrType, Limits limits) implements ExternalType {

	public enum AddrType {
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

		public abstract NumType asNumType();
		public abstract long unboxAddress(Object address);
		public abstract Object boxAddress(long address);
	}

}
