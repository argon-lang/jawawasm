package jvmwasm.engine;

import jvmwasm.format.data.V128;
import jvmwasm.format.types.NumType;
import jvmwasm.format.types.ValType;
import jvmwasm.format.types.VecType;
import jvmwasm.format.types.RefType;

public final class Defaults {
	private Defaults() {}

	public static Object defaultValue(ValType t) {
		return switch(t) {
			case NumType numType -> switch(numType) {
				case I32 -> 0;
				case I64 -> 0L;
				case F32 -> 0.0f;
				case F64 -> 0.0;
			};

			case RefType refType -> null;

			case VecType vecType -> switch(vecType) {
				case V128 -> V128.splat8((byte)0);
			};
		};
	}
}
