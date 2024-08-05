package dev.argon.jvmwasm.engine.validator;

import dev.argon.jvmwasm.format.types.ValType;

sealed interface OperandType {
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
