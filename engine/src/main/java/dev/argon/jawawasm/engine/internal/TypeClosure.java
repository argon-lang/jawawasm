package dev.argon.jawawasm.engine.internal;

import dev.argon.jawawasm.format.types.HeapType;
import dev.argon.jawawasm.format.types.RecTypeIdx;

public non-sealed abstract class TypeClosure extends TypeResolver {


	@Override
	public HeapType resolveRecTypeIdx(RecTypeIdx idx) {
		return idx;
	}
}
