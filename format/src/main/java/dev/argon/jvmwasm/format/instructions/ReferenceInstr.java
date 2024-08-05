package dev.argon.jvmwasm.format.instructions;

import dev.argon.jvmwasm.format.modules.FuncIdx;
import dev.argon.jvmwasm.format.types.RefType;

/**
 * Reference instructions.
 */
public sealed interface ReferenceInstr extends Instr {
	/**
	 * WebAssembly `ref.null` instruction
	 * @param type The type of the reference.
	 */
	public static record Ref_Null(RefType type) implements ReferenceInstr {}

	/**
	 * WebAssembly `ref.is_null` instruction
	 */
	public static record Ref_IsNull() implements ReferenceInstr {}

	/**
	 * WebAssembly `ref.func` instruction
	 * @param func The function index.
	 */
	public static record Ref_Func(FuncIdx func) implements ReferenceInstr {}
}
