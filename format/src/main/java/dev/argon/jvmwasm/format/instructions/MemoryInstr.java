package dev.argon.jvmwasm.format.instructions;

import dev.argon.jvmwasm.format.modules.DataIdx;

/**
 * A WebAssembly memory instruction
 */
public sealed interface MemoryInstr extends Instr {
	/**
	 * WebAssembly memory argument
	 * @param offset Fixed address offset to the instruction operand.
	 * @param align Alignment of the memory.
	 */
	public static record MemArg(int offset, int align) {}

	/**
	 * WebAssembly `inn.load` instruction
	 * @param numSize The size of the number (nn).
	 * @param memArg The memory argument.
	 */
	public static record Inn_Load(NumericInstr.NumSize numSize, MemArg memArg) implements MemoryInstr {}


	/**
	 * WebAssembly `fnn.load` instruction
	 * @param numSize The size of the number (nn).
	 * @param memArg The memory argument.
	 */
	public static record Fnn_Load(NumericInstr.NumSize numSize, MemArg memArg) implements MemoryInstr {}


	/**
	 * WebAssembly `inn.store` instruction
	 * @param numSize The size of the number (nn).
	 * @param memArg The memory argument.
	 */
	public static record Inn_Store(NumericInstr.NumSize numSize, MemArg memArg) implements MemoryInstr {}


	/**
	 * WebAssembly `fnn.store` instruction
	 * @param numSize The size of the number (nn).
	 * @param memArg The memory argument.
	 */
	public static record Fnn_Store(NumericInstr.NumSize numSize, MemArg memArg) implements MemoryInstr {}


	/**
	 * WebAssembly `v128.load` instruction
	 * @param memArg The memory argument.
	 */
	public static record V128_Load(MemArg memArg) implements MemoryInstr {}

	/**
	 * WebAssembly `v128.store` instruction
	 * @param memArg The memory argument.
	 */
	public static record V128_Store(MemArg memArg) implements MemoryInstr {}



	/**
	 * WebAssembly `inn.load8_u` instruction
	 * @param numSize The size of the number (nn).
	 * @param memArg The memory argument.
	 */
	public static record Inn_Load8_U(NumericInstr.NumSize numSize, MemArg memArg) implements MemoryInstr {}

	/**
	 * WebAssembly `inn.load8_s` instruction
	 * @param numSize The size of the number (nn).
	 * @param memArg The memory argument.
	 */
	public static record Inn_Load8_S(NumericInstr.NumSize numSize, MemArg memArg) implements MemoryInstr {}

	/**
	 * WebAssembly `inn.load16_u` instruction
	 * @param numSize The size of the number (nn).
	 * @param memArg The memory argument.
	 */
	public static record Inn_Load16_U(NumericInstr.NumSize numSize, MemArg memArg) implements MemoryInstr {}

	/**
	 * WebAssembly `inn.load16_s` instruction
	 * @param numSize The size of the number (nn).
	 * @param memArg The memory argument.
	 */
	public static record Inn_Load16_S(NumericInstr.NumSize numSize, MemArg memArg) implements MemoryInstr {}

	/**
	 * WebAssembly `i64.load32_u` instruction
	 * @param memArg The memory argument.
	 */
	public static record I64_Load32_U(MemArg memArg) implements MemoryInstr {}

	/**
	 * WebAssembly `i64.load32_s` instruction
	 * @param memArg The memory argument.
	 */
	public static record I64_Load32_S(MemArg memArg) implements MemoryInstr {}


	/**
	 * WebAssembly `inn.store8` instruction
	 * @param numSize The size of the number (nn).
	 * @param memArg The memory argument.
	 */
	public static record Inn_Store8(NumericInstr.NumSize numSize, MemArg memArg) implements MemoryInstr {}
	
	/**
	 * WebAssembly `inn.store16` instruction
	 * @param numSize The size of the number (nn).
	 * @param memArg The memory argument.
	 */
	public static record Inn_Store16(NumericInstr.NumSize numSize, MemArg memArg) implements MemoryInstr {}

	/**
	 * WebAssembly `i64.store32` instruction
	 * @param memArg The memory argument.
	 */
	public static record I64_Store32(MemArg memArg) implements MemoryInstr {}


	/**
	 * WebAssembly `v128.load8x8_u` instruction
	 * @param memArg The memory argument.
	 */
	public static record V128_Load8x8_U(MemArg memArg) implements MemoryInstr {}

	/**
	 * WebAssembly `v128.load8x8_s` instruction
	 * @param memArg The memory argument.
	 */
	public static record V128_Load8x8_S(MemArg memArg) implements MemoryInstr {}

	/**
	 * WebAssembly `v128.load16x4_u` instruction
	 * @param memArg The memory argument.
	 */
	public static record V128_Load16x4_U(MemArg memArg) implements MemoryInstr {}

	/**
	 * WebAssembly `v128.load16x4_s` instruction
	 * @param memArg The memory argument.
	 */
	public static record V128_Load16x4_S(MemArg memArg) implements MemoryInstr {}

	/**
	 * WebAssembly `v128.load32x2_u` instruction
	 * @param memArg The memory argument.
	 */
	public static record V128_Load32x2_U(MemArg memArg) implements MemoryInstr {}

	/**
	 * WebAssembly `v128.load32x2_s` instruction
	 * @param memArg The memory argument.
	 */
	public static record V128_Load32x2_S(MemArg memArg) implements MemoryInstr {}

	/**
	 * WebAssembly `v128.load32_zero` instruction
	 * @param memArg The memory argument.
	 */
	public static record V128_Load32_Zero(MemArg memArg) implements MemoryInstr {}

	/**
	 * WebAssembly `v128.load64_zero` instruction
	 * @param memArg The memory argument.
	 */
	public static record V128_Load64_Zero(MemArg memArg) implements MemoryInstr {}


	/**
	 * WebAssembly `v128.load8_splat` instruction
	 * @param memArg The memory argument.
	 */
	public static record V128_Load8_Splat(MemArg memArg) implements MemoryInstr {}

	/**
	 * WebAssembly `v128.load16_splat` instruction
	 * @param memArg The memory argument.
	 */
	public static record V128_Load16_Splat(MemArg memArg) implements MemoryInstr {}

	/**
	 * WebAssembly `v128.load32_splat` instruction
	 * @param memArg The memory argument.
	 */
	public static record V128_Load32_Splat(MemArg memArg) implements MemoryInstr {}

	/**
	 * WebAssembly `v128.load64_splat` instruction
	 * @param memArg The memory argument.
	 */
	public static record V128_Load64_Splat(MemArg memArg) implements MemoryInstr {}

	/**
	 * WebAssembly `v128.load8_lane` instruction
	 * @param memArg The memory argument.
	 * @param laneIdx The lane index.   
	 */
	public static record V128_Load8_Lane(MemArg memArg, byte laneIdx) implements MemoryInstr {}

	/**
	 * WebAssembly `v128.load16_lane` instruction
	 * @param memArg The memory argument.
	 * @param laneIdx The lane index.   
	 */
	public static record V128_Load16_Lane(MemArg memArg, byte laneIdx) implements MemoryInstr {}

	/**
	 * WebAssembly `v128.load32_lane` instruction
	 * @param memArg The memory argument.
	 * @param laneIdx The lane index.   
	 */
	public static record V128_Load32_Lane(MemArg memArg, byte laneIdx) implements MemoryInstr {}

	/**
	 * WebAssembly `v128.load64_lane` instruction
	 * @param memArg The memory argument.
	 * @param laneIdx The lane index.   
	 */
	public static record V128_Load64_Lane(MemArg memArg, byte laneIdx) implements MemoryInstr {}

	/**
	 * WebAssembly `v128.store8_lane` instruction
	 * @param memArg The memory argument.
	 * @param laneIdx The lane index.   
	 */
	public static record V128_Store8_Lane(MemArg memArg, byte laneIdx) implements MemoryInstr {}

	/**
	 * WebAssembly `v128.store16_lane` instruction
	 * @param memArg The memory argument.
	 * @param laneIdx The lane index.   
	 */
	public static record V128_Store16_Lane(MemArg memArg, byte laneIdx) implements MemoryInstr {}

	/**
	 * WebAssembly `v128.store32_lane` instruction
	 * @param memArg The memory argument.
	 * @param laneIdx The lane index.   
	 */
	public static record V128_Store32_Lane(MemArg memArg, byte laneIdx) implements MemoryInstr {}

	/**
	 * WebAssembly `v128.store64_lane` instruction
	 * @param memArg The memory argument.
	 * @param laneIdx The lane index.   
	 */
	public static record V128_Store64_Lane(MemArg memArg, byte laneIdx) implements MemoryInstr {}

	/**
	 * WebAssembly `memory.size` instruction
	 */
	public static record Memory_Size() implements MemoryInstr {}

	/**
	 * WebAssembly `memory.grow` instruction
	 */
	public static record Memory_Grow() implements MemoryInstr {}

	/**
	 * WebAssembly `memory.fill` instruction
	 */
	public static record Memory_Fill() implements MemoryInstr {}

	/**
	 * WebAssembly `memory.copy` instruction
	 */
	public static record Memory_Copy() implements MemoryInstr {}

	/**
	 * WebAssembly `memory.init` instruction
	 * @param data The data index.
	 */
	public static record Memory_Init(DataIdx data) implements MemoryInstr {}

	/**
	 * WebAssembly `data.drop` instruction
	 * @param data The data index.
	 */
	public static record Data_Drop(DataIdx data) implements MemoryInstr {}


}
