package dev.argon.jawawasm.runtime;

import java.util.Objects;

public sealed abstract class WasmArray implements WasmEq permits WasmArrayMutable, WasmArrayImmutable {
	/**
	 * Gets the length of the array.
	 * @return The length.
	 */
	public abstract int length();

	/**
	 * Gets the underlying array.
	 * @return The array.
	 */
	protected abstract Object unsafeGetArray();


	public static void copy(WasmArrayMutable dest, int d, WasmArray src, int s, int n) {
		Objects.requireNonNull(dest);
		Objects.requireNonNull(src);
		Objects.checkFromIndexSize(s, n, src.length());
		Objects.checkFromIndexSize(d, n, dest.length());

		if(n == 0) {
			return;
		}

		System.arraycopy(src.unsafeGetArray(), s, dest.unsafeGetArray(), d, n);
	}
}
