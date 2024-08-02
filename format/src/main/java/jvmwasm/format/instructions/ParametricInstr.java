package jvmwasm.format.instructions;

import jvmwasm.format.types.ValType;
import org.jspecify.annotations.Nullable;

import java.util.List;

public sealed interface ParametricInstr extends Instr {
	public static record Drop() implements ParametricInstr {}
	public static record Select(@Nullable List<? extends ValType> types) implements ParametricInstr {}
}
