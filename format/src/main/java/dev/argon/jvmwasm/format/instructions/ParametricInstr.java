package dev.argon.jvmwasm.format.instructions;

import dev.argon.jvmwasm.format.types.ValType;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Parametric instructions
 */
public sealed interface ParametricInstr extends Instr {
	/**
	 * WebAssembly `drop` instruction
	 */
	public static record Drop() implements ParametricInstr {}

	/**
	 * WebAssembly `select` instruction
	 * @param types The operand types.
	 */
	public static record Select(@Nullable List<? extends ValType> types) implements ParametricInstr {}
}
