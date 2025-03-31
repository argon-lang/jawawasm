package dev.argon.jawawasm.runtime;

/**
 * Represents a Wasm heap value that can be compared with equality.
 */
public sealed interface WasmEq extends WasmObject permits I31, WasmArray, WasmStruct {
	/**
	 * Checks if two WebAssembly reference values are equal.
	 * @param a The first value.
	 * @param b The second value.
	 * @return true iff the values are equal.
	 */
	static boolean isEqual(WasmEq a, WasmEq b) {
		if(a == b) {
			return true;
		}

		if(a instanceof I31 an) {
			return an.equals(b);
		}

		return false;
	}
}
