package dev.argon.jawawasm.runtime;

/**
 * Represents a Wasm heap value that can be compared with equality.
 */
public sealed interface WasmEq extends WasmObject permits I31, WasmArray, WasmStruct {
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
