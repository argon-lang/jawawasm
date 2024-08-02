package jvmwasm.format.instructions;

import jvmwasm.format.modules.DataIdx;

public sealed interface MemoryInstr extends Instr {
	public static record MemArg(int offset, int align) {}

	public static record Inn_Load(NumericInstr.NumSize numSize, MemArg memArg) implements MemoryInstr {}
	public static record Fnn_Load(NumericInstr.NumSize numSize, MemArg memArg) implements MemoryInstr {}

	public static record Inn_Store(NumericInstr.NumSize numSize, MemArg memArg) implements MemoryInstr {}
	public static record Fnn_Store(NumericInstr.NumSize numSize, MemArg memArg) implements MemoryInstr {}

	public static record V128_Load(MemArg memArg) implements MemoryInstr {}
	public static record V128_Store(MemArg memArg) implements MemoryInstr {}



	public static record Inn_Load8_U(NumericInstr.NumSize numSize, MemArg memArg) implements MemoryInstr {}
	public static record Inn_Load8_S(NumericInstr.NumSize numSize, MemArg memArg) implements MemoryInstr {}
	public static record Inn_Load16_U(NumericInstr.NumSize numSize, MemArg memArg) implements MemoryInstr {}
	public static record Inn_Load16_S(NumericInstr.NumSize numSize, MemArg memArg) implements MemoryInstr {}
	public static record I64_Load32_U(MemArg memArg) implements MemoryInstr {}
	public static record I64_Load32_S(MemArg memArg) implements MemoryInstr {}

	public static record Inn_Store8(NumericInstr.NumSize numSize, MemArg memArg) implements MemoryInstr {}
	public static record Inn_Store16(NumericInstr.NumSize numSize, MemArg memArg) implements MemoryInstr {}
	public static record I64_Store32(MemArg memArg) implements MemoryInstr {}


	public static record V128_Load8x8_U(MemArg memArg) implements MemoryInstr {}
	public static record V128_Load8x8_S(MemArg memArg) implements MemoryInstr {}
	public static record V128_Load16x4_U(MemArg memArg) implements MemoryInstr {}
	public static record V128_Load16x4_S(MemArg memArg) implements MemoryInstr {}
	public static record V128_Load32x2_U(MemArg memArg) implements MemoryInstr {}
	public static record V128_Load32x2_S(MemArg memArg) implements MemoryInstr {}
	public static record V128_Load32_Zero(MemArg memArg) implements MemoryInstr {}
	public static record V128_Load64_Zero(MemArg memArg) implements MemoryInstr {}

	public static record V128_Load8_Splat(MemArg memArg) implements MemoryInstr {}
	public static record V128_Load16_Splat(MemArg memArg) implements MemoryInstr {}
	public static record V128_Load32_Splat(MemArg memArg) implements MemoryInstr {}
	public static record V128_Load64_Splat(MemArg memArg) implements MemoryInstr {}
	public static record V128_Load8_Lane(MemArg memArg, byte laneIdx) implements MemoryInstr {}
	public static record V128_Load16_Lane(MemArg memArg, byte laneIdx) implements MemoryInstr {}
	public static record V128_Load32_Lane(MemArg memArg, byte laneIdx) implements MemoryInstr {}
	public static record V128_Load64_Lane(MemArg memArg, byte laneIdx) implements MemoryInstr {}
	public static record V128_Store8_Lane(MemArg memArg, byte laneIdx) implements MemoryInstr {}
	public static record V128_Store16_Lane(MemArg memArg, byte laneIdx) implements MemoryInstr {}
	public static record V128_Store32_Lane(MemArg memArg, byte laneIdx) implements MemoryInstr {}
	public static record V128_Store64_Lane(MemArg memArg, byte laneIdx) implements MemoryInstr {}


	public static record Memory_Size() implements MemoryInstr {}
	public static record Memory_Grow() implements MemoryInstr {}
	public static record Memory_Fill() implements MemoryInstr {}
	public static record Memory_Copy() implements MemoryInstr {}
	public static record Memory_Init(DataIdx data) implements MemoryInstr {}
	public static record Data_Drop(DataIdx data) implements MemoryInstr {}


}
