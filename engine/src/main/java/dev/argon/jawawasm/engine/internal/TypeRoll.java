package dev.argon.jawawasm.engine.internal;

import dev.argon.jawawasm.format.modules.TypeIdx;
import dev.argon.jawawasm.format.types.HeapType;
import dev.argon.jawawasm.format.types.RecTypeIdx;
import dev.argon.jawawasm.format.types.RecursiveType;
import dev.argon.jawawasm.format.types.SubType;

import java.util.ArrayList;

public final class TypeRoll extends TypeResolver {
	private TypeRoll(int recTypeStart) {
		this.recTypeStart = recTypeStart;
	}

	private final int recTypeStart;

	@Override
	public HeapType resolveTypeIdx(TypeIdx idx) {
		if(idx.index() >= recTypeStart) {
			return new RecTypeIdx(idx.index() - recTypeStart);
		}
		else {
			return idx;
		}
	}

	@Override
	public HeapType resolveRecTypeIdx(RecTypeIdx idx) {
		return idx;
	}

	public static RecursiveType roll(RecursiveType t, int recTypeStart) {
		var roll = new TypeRoll(recTypeStart);
		return roll.resolveRecursiveType(t);
	}


}
