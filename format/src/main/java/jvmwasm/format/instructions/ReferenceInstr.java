package jvmwasm.format.instructions;

import jvmwasm.format.modules.FuncIdx;
import jvmwasm.format.types.RefType;

public sealed interface ReferenceInstr extends Instr {
	public static record Ref_Null(RefType type) implements ReferenceInstr {}
	public static record Ref_IsNull() implements ReferenceInstr {}
	public static record Ref_Func(FuncIdx func) implements ReferenceInstr {}
}
