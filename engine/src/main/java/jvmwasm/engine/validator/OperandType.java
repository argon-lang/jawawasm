package jvmwasm.engine.validator;

import jvmwasm.format.types.ValType;

public sealed interface OperandType {
	public static record OfValType(ValType valType) implements OperandType {
		@Override
		public String toString() {
			return valType().toString();
		}
	}
	public static final class Bottom implements OperandType {
		@Override
		public String toString() {
			return "Bottom";
		}
	}
}
