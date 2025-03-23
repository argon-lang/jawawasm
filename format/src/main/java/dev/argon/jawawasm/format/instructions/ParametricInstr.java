package dev.argon.jawawasm.format.instructions;

import com.google.common.collect.ImmutableList;
import dev.argon.jawawasm.format.types.ValType;
import org.jspecify.annotations.Nullable;

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
	public static record Select(@Nullable ImmutableList<ValType> types) implements ParametricInstr {}
}
