package dev.argon.jawawasm.format.instructions;

/**
 * An instruction.
 */
public sealed interface Instr extends InstrOrTerminator permits ControlInstr, MemoryInstr, NumericInstr, ParametricInstr, ReferenceInstr, TableInstr, VariableInstr, VectorInstr {
}
