package dev.argon.jawawasm.engine.internal;

import dev.argon.jawawasm.format.modules.TypeIdx;
import dev.argon.jawawasm.format.types.*;

import java.util.ArrayList;

public final class TypeUnroll extends TypeResolver {
	private TypeUnroll(RecursiveType recType) {
		this.recType = recType;
	}

	private final RecursiveType recType;

	@Override
	public HeapType resolveTypeIdx(TypeIdx idx) {
		return idx;
	}

	@Override
	public HeapType resolveRecTypeIdx(RecTypeIdx idx) {
		return new DefType(recType, idx.index());
	}

	public static RecursiveType unroll(RecursiveType t) {
		var unroll = new TypeUnroll(t);
		return unroll.resolveRecursiveType(t);
	}

	public static SubType unroll(DefType t) {
		var unroll = new TypeUnroll(t.recursiveType());
		return unroll.resolveSubType(t.recursiveType().subtypes().get(t.index()));
	}

	public static CompositeType expand(DefType t) {
		return unroll(t).compositeType();
	}
}
