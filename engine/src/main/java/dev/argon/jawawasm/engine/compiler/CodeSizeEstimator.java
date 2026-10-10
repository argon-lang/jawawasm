package dev.argon.jawawasm.engine.compiler;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Instruction;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.LookupSwitchInstruction;
import java.lang.classfile.instruction.TableSwitchInstruction;

final class CodeSizeEstimator implements CodeTransform {
	private long size;

	@Override
	public void atStart(CodeBuilder builder) {
		size = 0;
	}

	@Override
	public void accept(CodeBuilder builder, CodeElement element) {
		builder.with(element);
		if(element instanceof Instruction instruction) {
			size += instructionSize(instruction, size);
		}
	}

	public long estimatedSize() {
		return size;
	}

	private static long instructionSize(Instruction instruction, long offset) {
		long padding = (4 - ((offset + 1) & 3)) & 3;
		return switch(instruction) {
			case LookupSwitchInstruction lookup -> 1 + padding + 8 + 8L * lookup.cases().size();
			case TableSwitchInstruction table ->
				1 + padding + 12 + 4L * ((long)table.highValue() - table.lowValue() + 1);
			case BranchInstruction branch -> branch.opcode() == Opcode.GOTO || branch.opcode() == Opcode.GOTO_W ? 5 : 8;
			default -> instruction.opcode() == Opcode.LDC ? 3 : instruction.sizeInBytes();
		};
	}
}
