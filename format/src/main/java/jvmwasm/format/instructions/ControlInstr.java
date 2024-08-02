package jvmwasm.format.instructions;

import jvmwasm.format.modules.FuncIdx;
import jvmwasm.format.modules.LabelIdx;
import jvmwasm.format.modules.TableIdx;
import jvmwasm.format.modules.TypeIdx;
import jvmwasm.format.types.ValType;

import java.util.List;

public sealed interface ControlInstr extends Instr {
	public sealed interface BlockType {
		public static record Empty() implements BlockType {}
		public static record OfIndex(TypeIdx type) implements BlockType {}
		public static record OfValType(ValType type) implements BlockType {}
	}

	public static record Nop() implements ControlInstr {}
	public static record Unreachable() implements ControlInstr {}
	public static record Block(BlockType type, List<? extends Instr> body) implements ControlInstr {}
	public static record Loop(BlockType type, List<? extends Instr> body) implements ControlInstr {}
	public static record If(BlockType type, List<? extends Instr> thenBody, List<? extends Instr> elseBody) implements ControlInstr {}
	public static record Br(LabelIdx label) implements ControlInstr {}
	public static record Br_If(LabelIdx label) implements ControlInstr {}
	public static record Br_Table(List<? extends LabelIdx> labels, LabelIdx fallback) implements ControlInstr {}
	public static record Return() implements ControlInstr {}
	public static record Call(FuncIdx func) implements ControlInstr {}
	public static record Call_Indirect(TableIdx table, TypeIdx funcType) implements ControlInstr {}
	public static record Return_Call(FuncIdx func) implements ControlInstr {}
	public static record Return_Call_Indirect(TableIdx table, TypeIdx funcType) implements ControlInstr {}
}
