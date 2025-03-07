package dev.argon.jawawasm.engine;

import dev.argon.jawawasm.format.types.HeapType;

public final class I31 implements WasmEq {
	public I31(int value) {
		this.value = (value << 1) >> 1;
	}


	private final int value;

	public int signedValue() {
		return value;
	}

	public int unsignedValue() {
		return value & 0x7FFFFFFF;
	}

	@Override
	public HeapType heapType() {
		return HeapType.AbstractHeapType.I31;
	}

	@Override
	public boolean equals(Object obj) {
		if(!(obj instanceof I31 other)) {
			return false;
		}

		return value == other.value;
	}

	@Override
	public int hashCode() {
		return value;
	}
}
