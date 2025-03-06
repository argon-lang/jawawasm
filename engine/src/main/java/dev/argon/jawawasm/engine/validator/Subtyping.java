package dev.argon.jawawasm.engine.validator;

import dev.argon.jawawasm.engine.internal.SubtypingBase;
import dev.argon.jawawasm.format.modules.TypeIdx;
import dev.argon.jawawasm.format.types.*;

class Subtyping extends SubtypingBase {

	public Subtyping(Context context) {
		this.context = context;
	}

	private final Context context;

	@Override
	public HeapType resolveTypeIdx(TypeIdx idx) {
		return context.getType(idx);
	}
}
