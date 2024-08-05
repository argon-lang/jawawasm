package dev.argon.jvmwasm.format.instructions;

import dev.argon.jvmwasm.format.modules.FuncIdx;
import dev.argon.jvmwasm.format.modules.LabelIdx;
import dev.argon.jvmwasm.format.modules.TableIdx;
import dev.argon.jvmwasm.format.modules.TypeIdx;
import dev.argon.jvmwasm.format.types.ValType;

import java.util.List;

/**
 * Control instructions
 */
public sealed interface ControlInstr extends Instr {
	/**
	 * The type of a block.
	 */
	public sealed interface BlockType {
		/**
		 * A block that returns no values.
		 */
		public static record Empty() implements BlockType {}

		/**
		 * A block that returns a value of a type represented by a type index.
		 * @param type The type index.
		 */
		public static record OfIndex(TypeIdx type) implements BlockType {}

		/**
		 * A block that returns a value represented by a value type.
		 * @param type The value type.
		 */
		public static record OfValType(ValType type) implements BlockType {}
	}

	/**
	 * WebAssembly `nop` instruction
	 */
	public static record Nop() implements ControlInstr {}

	/**
	 * WebAssembly `unreachable` instruction
	 */
	public static record Unreachable() implements ControlInstr {}

	/**
	 * WebAssembly `block` instruction
	 * @param type The type of the block.
	 * @param body The body of the block.
	 */
	public static record Block(BlockType type, List<? extends Instr> body) implements ControlInstr {}

	/**
	 * WebAssembly `loop` instruction
	 * @param type The type of the block.
	 * @param body The body of the block.
	 */
	public static record Loop(BlockType type, List<? extends Instr> body) implements ControlInstr {}

	/**
	 * WebAssembly `if` instruction
	 * @param type The type of the block.
	 * @param thenBody The body of the block executed when true.
	 * @param elseBody The body of the block executed when false.
	 */
	public static record If(BlockType type, List<? extends Instr> thenBody, List<? extends Instr> elseBody) implements ControlInstr {}

	/**
	 * WebAssembly `br` instruction
	 * @param label The branch target.
	 */
	public static record Br(LabelIdx label) implements ControlInstr {}

	/**
	 * WebAssembly `br_if` instruction
	 * @param label The branch target.
	 */
	public static record Br_If(LabelIdx label) implements ControlInstr {}

	/**
	 * WebAssembly `br_table` instruction
	 * @param labels The branch targets.
	 * @param fallback The fallback branch target.
	 */
	public static record Br_Table(List<? extends LabelIdx> labels, LabelIdx fallback) implements ControlInstr {}

	/**
	 * WebAssembly `return` instruction
	 */
	public static record Return() implements ControlInstr {}

	/**
	 * WebAssembly `call` instruction
	 * @param func The function to call.
	 */
	public static record Call(FuncIdx func) implements ControlInstr {}

	/**
	 * WebAssembly `call_indirect` instruction
	 * @param table The table containing the function index.
	 * @param funcType The function type.
	 */
	public static record Call_Indirect(TableIdx table, TypeIdx funcType) implements ControlInstr {}

	/**
	 * WebAssembly `return_call` instruction
	 * @param func The function to call.
	 */
	public static record Return_Call(FuncIdx func) implements ControlInstr {}

	/**
	 * WebAssembly `return_call_indirect` instruction
	 * @param table The table containing the function index.
	 * @param funcType The function type.
	 */
	public static record Return_Call_Indirect(TableIdx table, TypeIdx funcType) implements ControlInstr {}

}
