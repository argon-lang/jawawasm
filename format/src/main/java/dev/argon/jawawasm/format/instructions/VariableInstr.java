package dev.argon.jawawasm.format.instructions;

import dev.argon.jawawasm.format.modules.GlobalIdx;
import dev.argon.jawawasm.format.modules.LocalIdx;

/**
 * Variable instructions
 */
public sealed interface VariableInstr extends Instr {
	/**
	 * WebAssembly `local.get` instruction
	 * @param local The local index.
	 */
	public static record Local_Get(LocalIdx local) implements VariableInstr {}
	/**
	 * WebAssembly `local.set` instruction
	 * @param local The local index.
	 */
	public static record Local_Set(LocalIdx local) implements VariableInstr {}
	/**
	 * WebAssembly `local.tee` instruction
	 * @param local The local index.
	 */
	public static record Local_Tee(LocalIdx local) implements VariableInstr {}
	/**
	 * WebAssembly `global.get` instruction
	 * @param global The global index.
	 */
	public static record Global_Get(GlobalIdx global) implements VariableInstr {}
	/**
	 * WebAssembly `global.set` instruction
	 * @param global The global index.
	 */
	public static record Global_Set(GlobalIdx global) implements VariableInstr {}
}
