package dev.argon.jawawasm.format.instructions;

import dev.argon.jawawasm.format.modules.*;
import dev.argon.jawawasm.format.types.RefType;
import dev.argon.jawawasm.format.types.ValType;

import java.util.List;

/**
 * Represents WebAssembly control instructions.
 */
public sealed interface ControlInstr extends Instr {

	/**
	 * Represents the type of a block in WebAssembly.
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
	 * WebAssembly `nop` instruction.
	 */
	public static record Nop() implements ControlInstr {}

	/**
	 * WebAssembly `unreachable` instruction.
	 */
	public static record Unreachable() implements ControlInstr {}

	/**
	 * WebAssembly `block` instruction.
	 * @param type The type of the block.
	 * @param body The body of the block.
	 */
	public static record Block(BlockType type, List<? extends Instr> body) implements ControlInstr {}

	/**
	 * WebAssembly `loop` instruction.
	 * @param type The type of the block.
	 * @param body The body of the block.
	 */
	public static record Loop(BlockType type, List<? extends Instr> body) implements ControlInstr {}

	/**
	 * WebAssembly `if` instruction.
	 * @param type The type of the block.
	 * @param thenBody The body of the block executed when true.
	 * @param elseBody The body of the block executed when false.
	 */
	public static record If(BlockType type, List<? extends Instr> thenBody, List<? extends Instr> elseBody) implements ControlInstr {}

	/**
	 * WebAssembly `throw` instruction.
	 * @param tag The exception tag index.
	 */
	public static record Throw(TagIdx tag) implements ControlInstr {}

	/**
	 * WebAssembly `throw_ref` instruction.
	 */
	public static record Throw_Ref() implements ControlInstr {}

	/**
	 * WebAssembly `br` instruction.
	 * @param label The branch target.
	 */
	public static record Br(LabelIdx label) implements ControlInstr {}

	/**
	 * WebAssembly `br_if` instruction.
	 * @param label The branch target.
	 */
	public static record Br_If(LabelIdx label) implements ControlInstr {}

	/**
	 * WebAssembly `br_table` instruction.
	 * @param labels The branch targets.
	 * @param fallback The fallback branch target.
	 */
	public static record Br_Table(List<? extends LabelIdx> labels, LabelIdx fallback) implements ControlInstr {}

	/**
	 * WebAssembly `br_on_null` instruction.
	 * @param label The branch target if the reference is null.
	 */
	public static record Br_OnNull(LabelIdx label) implements ControlInstr {}

	/**
	 * WebAssembly `br_on_non_null` instruction.
	 * @param label The branch target if the reference is not null.
	 */
	public static record Br_OnNonNull(LabelIdx label) implements ControlInstr {}

	/**
	 * WebAssembly `br_on_cast` instruction.
	 * @param label The branch target if the cast succeeds.
	 * @param t1 The source reference type.
	 * @param t2 The destination reference type.
	 */
	public static record Br_OnCast(LabelIdx label, RefType t1, RefType t2) implements ControlInstr {}

	/**
	 * WebAssembly `br_on_cast_fail` instruction.
	 * @param label The branch target if the cast fails.
	 * @param t1 The source reference type.
	 * @param t2 The destination reference type.
	 */
	public static record Br_OnCastFail(LabelIdx label, RefType t1, RefType t2) implements ControlInstr {}

	/**
	 * WebAssembly `return` instruction.
	 */
	public static record Return() implements ControlInstr {}

	/**
	 * WebAssembly `call` instruction.
	 * @param func The function to call.
	 */
	public static record Call(FuncIdx func) implements ControlInstr {}

	/**
	 * WebAssembly `call_ref` instruction.
	 * @param funcType The function type.
	 */
	public static record Call_Ref(TypeIdx funcType) implements ControlInstr {}

	/**
	 * WebAssembly `call_indirect` instruction.
	 * @param table The table containing the function index.
	 * @param funcType The function type.
	 */
	public static record Call_Indirect(TableIdx table, TypeIdx funcType) implements ControlInstr {}

	/**
	 * WebAssembly `return_call` instruction.
	 * @param func The function to call.
	 */
	public static record Return_Call(FuncIdx func) implements ControlInstr {}

	/**
	 * WebAssembly `return_call_ref` instruction.
	 * @param funcType The function type.
	 */
	public static record Return_Call_Ref(TypeIdx funcType) implements ControlInstr {}

	/**
	 * WebAssembly `return_call_indirect` instruction.
	 * @param table The table containing the function index.
	 * @param funcType The function type.
	 */
	public static record Return_Call_Indirect(TableIdx table, TypeIdx funcType) implements ControlInstr {}

	/**
	 * WebAssembly `try_table` instruction.
	 * @param blockType The type of the block.
	 * @param catchClauses The list of catch clauses.
	 * @param body The body of the try block.
	 */
	public static record Try_Table(BlockType blockType, List<? extends CatchClause> catchClauses, List<? extends Instr> body) implements ControlInstr {}

	/**
	 * Represents a catch clause in a `try_table` instruction.
	 */
	public static sealed interface CatchClause {}

	/**
	 * Represents a catch clause for a specific exception tag.
	 * @param tagIdx The tag index.
	 * @param labelIdx The branch target for the catch block.
	 */
	public static record CatchTag(TagIdx tagIdx, LabelIdx labelIdx) implements CatchClause {}

	/**
	 * Represents a catch clause for a specific exception tag using a reference.
	 * @param tagIdx The tag index.
	 * @param labelIdx The branch target for the catch block.
	 */
	public static record CatchTagRef(TagIdx tagIdx, LabelIdx labelIdx) implements CatchClause {}

	/**
	 * Represents a catch clause that catches all exceptions.
	 * @param labelIdx The branch target for the catch block.
	 */
	public static record CatchAll(LabelIdx labelIdx) implements CatchClause {}

	/**
	 * Represents a catch clause that catches all exceptions using a reference.
	 * @param labelIdx The branch target for the catch block.
	 */
	public static record CatchAllRef(LabelIdx labelIdx) implements CatchClause {}
}
