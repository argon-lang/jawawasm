package jvmwasm.format.instructions;

public sealed interface Instr extends InstrOrTerminator permits ControlInstr, MemoryInstr, NumericInstr, ParametricInstr, ReferenceInstr, TableInstr, VariableInstr, VectorInstr {
}
