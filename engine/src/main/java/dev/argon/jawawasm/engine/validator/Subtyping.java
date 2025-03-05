package dev.argon.jawawasm.engine.validator;

import dev.argon.jawawasm.engine.SubtypingBase;
import dev.argon.jawawasm.format.modules.TypeIdx;
import dev.argon.jawawasm.format.types.*;

class Subtyping extends SubtypingBase {

	public Subtyping(Context context) {
		this.context = context;
	}

	private final Context context;

	@Override
	protected CompositeType resolveTypeIdx(TypeIdx idx) {
		return resolveCompositeType(context.getType(idx));
	}
}
