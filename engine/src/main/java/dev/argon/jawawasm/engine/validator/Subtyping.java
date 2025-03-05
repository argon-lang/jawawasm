package dev.argon.jawawasm.engine.validator;

import dev.argon.jawawasm.format.modules.Global;
import dev.argon.jawawasm.format.modules.TypeIdx;
import dev.argon.jawawasm.format.types.*;

class Subtyping extends SubtypingBase {

	public Subtyping(Context context) {
		this.context = context;
	}

	private final Context context;

	@Override
	protected FuncType resolveTypeIdx(TypeIdx idx) {
		return context.getType(idx);
	}
}
