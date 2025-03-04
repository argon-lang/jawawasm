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
		},
		I64 {
			@Override
			public NumType asNumType() {
				return NumType.I64;
			}
		},
		;

		public abstract NumType asNumType();
	}

}
