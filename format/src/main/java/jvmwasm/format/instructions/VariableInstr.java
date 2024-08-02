package jvmwasm.format.instructions;

import jvmwasm.format.modules.GlobalIdx;
import jvmwasm.format.modules.LocalIdx;

public sealed interface VariableInstr extends Instr {
	public static record Local_Get(LocalIdx local) implements VariableInstr {}
	public static record Local_Set(LocalIdx local) implements VariableInstr {}
	public static record Local_Tee(LocalIdx local) implements VariableInstr {}
	public static record Global_Get(GlobalIdx global) implements VariableInstr {}
	public static record Global_Set(GlobalIdx global) implements VariableInstr {}
}
