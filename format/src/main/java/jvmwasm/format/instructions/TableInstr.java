package jvmwasm.format.instructions;

import jvmwasm.format.modules.ElemIdx;
import jvmwasm.format.modules.TableIdx;

public sealed interface TableInstr extends Instr {
	public static record Table_Get(TableIdx table) implements TableInstr {}
	public static record Table_Set(TableIdx table) implements TableInstr {}
	public static record Table_Size(TableIdx table) implements TableInstr {}
	public static record Table_Grow(TableIdx table) implements TableInstr {}
	public static record Table_Fill(TableIdx table) implements TableInstr {}
	public static record Table_Copy(TableIdx dest, TableIdx src) implements TableInstr {}
	public static record Table_Init(TableIdx table, ElemIdx elem) implements TableInstr {}
	public static record Elem_Drop(ElemIdx elem) implements TableInstr {}


}
