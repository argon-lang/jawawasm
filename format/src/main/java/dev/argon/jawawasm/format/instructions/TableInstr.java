package dev.argon.jawawasm.format.instructions;

import dev.argon.jawawasm.format.modules.ElemIdx;
import dev.argon.jawawasm.format.modules.TableIdx;

/**
 * Table instructions
 */
public sealed interface TableInstr extends Instr {
	/**
	 * WebAssembly `table.get` instruction
	 * @param table The table index.
	 */
	public static record Table_Get(TableIdx table) implements TableInstr {}
	/**
	 * WebAssembly `table.set` instruction
	 * @param table The table index.
	 */
	public static record Table_Set(TableIdx table) implements TableInstr {}
	/**
	 * WebAssembly `table.size` instruction
	 * @param table The table index.
	 */
	public static record Table_Size(TableIdx table) implements TableInstr {}
	/**
	 * WebAssembly `table.grow` instruction
	 * @param table The table index.
	 */
	public static record Table_Grow(TableIdx table) implements TableInstr {}
	/**
	 * WebAssembly `table.fill` instruction
	 * @param table The table index.
	 */
	public static record Table_Fill(TableIdx table) implements TableInstr {}
	/**
	 * WebAssembly `table.copy` instruction
	 * @param dest The destination table index.
	 * @param src The source table index.
	 */
	public static record Table_Copy(TableIdx dest, TableIdx src) implements TableInstr {}
	/**
	 * WebAssembly `table.init` instruction
	 * @param table The table index.
	 * @param elem The elem index.
	 */
	public static record Table_Init(TableIdx table, ElemIdx elem) implements TableInstr {}
	/**
	 * WebAssembly `elem.drop` instruction
	 * @param elem The elem index.
	 */
	public static record Elem_Drop(ElemIdx elem) implements TableInstr {}


}
