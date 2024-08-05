package dev.argon.jvmwasm.format.instructions;

/**
 * An instruction.
 */
public sealed interface Instr extends InstrOrTerminator permits ControlInstr, MemoryInstr, NumericInstr, ParametricInstr, ReferenceInstr, TableInstr, VariableInstr, VectorInstr {
}
