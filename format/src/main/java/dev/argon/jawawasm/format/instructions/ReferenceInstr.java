package dev.argon.jawawasm.format.instructions;

import dev.argon.jawawasm.format.modules.*;
import dev.argon.jawawasm.format.types.HeapType;
import dev.argon.jawawasm.format.types.RefType;

/**
 * Represents WebAssembly reference instructions.
 */
public sealed interface ReferenceInstr extends Instr {

	/**
	 * WebAssembly `ref.null` instruction.
	 * @param type The type of the reference.
	 */
	public static record Ref_Null(HeapType type) implements ReferenceInstr {}

	/**
	 * WebAssembly `ref.is_null` instruction.
	 */
	public static record Ref_IsNull() implements ReferenceInstr {}

	/**
	 * WebAssembly `ref.func` instruction.
	 * @param func The function index.
	 */
	public static record Ref_Func(FuncIdx func) implements ReferenceInstr {}

	/**
	 * WebAssembly `ref.eq` instruction.
	 */
	public static record Ref_Eq() implements ReferenceInstr {}

	/**
	 * WebAssembly `ref.as_non_null` instruction.
	 */
	public static record Ref_AsNonNull() implements ReferenceInstr {}

	/**
	 * WebAssembly `ref.test` instruction.
	 * @param t The reference type being tested.
	 */
	public record Ref_Test(RefType t) implements ReferenceInstr {}

	/**
	 * WebAssembly `ref.cast` instruction.
	 * @param t The reference type being cast.
	 */
	public record Ref_Cast(RefType t) implements ReferenceInstr {}

	/**
	 * WebAssembly `ref.i31` instruction.
	 */
	public record Ref_I31() implements ReferenceInstr {}

	/**
	 * WebAssembly `struct.new` instruction.
	 * @param typeIdx The index of the struct type.
	 */
	public record Struct_New(TypeIdx typeIdx) implements ReferenceInstr {}

	/**
	 * WebAssembly `struct.new_default` instruction.
	 * @param typeIdx The index of the struct type.
	 */
	public record Struct_New_Default(TypeIdx typeIdx) implements ReferenceInstr {}

	/**
	 * WebAssembly `struct.get` instruction.
	 * @param typeIdx The struct type index.
	 * @param fieldIdx The field index.
	 */
	public record Struct_Get(TypeIdx typeIdx, FieldIdx fieldIdx) implements ReferenceInstr {}

	/**
	 * WebAssembly `struct.get_s` instruction (signed).
	 * @param typeIdx The struct type index.
	 * @param fieldIdx The field index.
	 */
	public record Struct_Get_S(TypeIdx typeIdx, FieldIdx fieldIdx) implements ReferenceInstr {}

	/**
	 * WebAssembly `struct.get_u` instruction (unsigned).
	 * @param typeIdx The struct type index.
	 * @param fieldIdx The field index.
	 */
	public record Struct_Get_U(TypeIdx typeIdx, FieldIdx fieldIdx) implements ReferenceInstr {}

	/**
	 * WebAssembly `struct.set` instruction.
	 * @param typeIdx The struct type index.
	 * @param fieldIdx The field index.
	 */
	public record Struct_Set(TypeIdx typeIdx, FieldIdx fieldIdx) implements ReferenceInstr {}

	/**
	 * WebAssembly `array.new` instruction.
	 * @param typeIdx The array type index.
	 */
	public record Array_New(TypeIdx typeIdx) implements ReferenceInstr {}

	/**
	 * WebAssembly `array.new_fixed` instruction.
	 * @param typeIdx The array type index.
	 * @param size The fixed size of the array.
	 */
	public record Array_New_Fixed(TypeIdx typeIdx, int size) implements ReferenceInstr {}

	/**
	 * WebAssembly `array.new_default` instruction.
	 * @param typeIdx The array type index.
	 */
	public record Array_New_Default(TypeIdx typeIdx) implements ReferenceInstr {}

	/**
	 * WebAssembly `array.new_data` instruction.
	 * @param typeIdx The array type index.
	 * @param dataIdx The data index.
	 */
	public record Array_New_Data(TypeIdx typeIdx, DataIdx dataIdx) implements ReferenceInstr {}

	/**
	 * WebAssembly `array.new_elem` instruction.
	 * @param typeIdx The array type index.
	 * @param elemIdx The element index.
	 */
	public record Array_New_Elem(TypeIdx typeIdx, ElemIdx elemIdx) implements ReferenceInstr {}

	/**
	 * WebAssembly `array.get` instruction.
	 * @param typeIdx The array type index.
	 */
	public record Array_Get(TypeIdx typeIdx) implements ReferenceInstr {}

	/**
	 * WebAssembly `array.get_s` instruction (signed).
	 * @param typeIdx The array type index.
	 */
	public record Array_Get_S(TypeIdx typeIdx) implements ReferenceInstr {}

	/**
	 * WebAssembly `array.get_u` instruction (unsigned).
	 * @param typeIdx The array type index.
	 */
	public record Array_Get_U(TypeIdx typeIdx) implements ReferenceInstr {}

	/**
	 * WebAssembly `array.set` instruction.
	 * @param typeIdx The array type index.
	 */
	public record Array_Set(TypeIdx typeIdx) implements ReferenceInstr {}

	/**
	 * WebAssembly `array.len` instruction.
	 */
	public record Array_Len() implements ReferenceInstr {}

	/**
	 * WebAssembly `array.fill` instruction.
	 * @param typeIdx The array type index.
	 */
	public record Array_Fill(TypeIdx typeIdx) implements ReferenceInstr {}

	/**
	 * WebAssembly `array.copy` instruction.
	 * @param dest The destination array type index.
	 * @param src The source array type index.
	 */
	public record Array_Copy(TypeIdx dest, TypeIdx src) implements ReferenceInstr {}

	/**
	 * WebAssembly `array.init_data` instruction.
	 * @param typeIdx The array type index.
	 * @param dataIdx The data index.
	 */
	public record Array_Init_Data(TypeIdx typeIdx, DataIdx dataIdx) implements ReferenceInstr {}

	/**
	 * WebAssembly `array.init_elem` instruction.
	 * @param typeIdx The array type index.
	 * @param elemIdx The element index.
	 */
	public record Array_Init_Elem(TypeIdx typeIdx, ElemIdx elemIdx) implements ReferenceInstr {}

	/**
	 * WebAssembly `i31.get_s` instruction (signed).
	 */
	public record I31_Get_S() implements ReferenceInstr {}

	/**
	 * WebAssembly `i31.get_u` instruction (unsigned).
	 */
	public record I31_Get_U() implements ReferenceInstr {}

	/**
	 * WebAssembly `any.convert.extern` instruction.
	 */
	public record Any_Convert_Extern() implements ReferenceInstr {}

	/**
	 * WebAssembly `extern.convert.any` instruction.
	 */
	public record Extern_Convert_Any() implements ReferenceInstr {}
}
