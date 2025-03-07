package dev.argon.jawawasm.format.instructions;

import dev.argon.jawawasm.format.modules.*;
import dev.argon.jawawasm.format.types.HeapType;
import dev.argon.jawawasm.format.types.RefType;

/**
 * Reference instructions.
 */
public sealed interface ReferenceInstr extends Instr {
	/**
	 * WebAssembly `ref.null` instruction
	 * @param type The type of the reference.
	 */
	public static record Ref_Null(HeapType type) implements ReferenceInstr {}

	/**
	 * WebAssembly `ref.is_null` instruction
	 */
	public static record Ref_IsNull() implements ReferenceInstr {}

	/**
	 * WebAssembly `ref.func` instruction
	 * @param func The function index.
	 */
	public static record Ref_Func(FuncIdx func) implements ReferenceInstr {}

	public static record Ref_Eq() implements ReferenceInstr {}

	public static record Ref_AsNonNull() implements ReferenceInstr {}

	public record Ref_Test(RefType t) implements ReferenceInstr {}
	public record Ref_Cast(RefType t) implements ReferenceInstr {}

	public record Ref_I31() implements ReferenceInstr {}


	public record Struct_New(TypeIdx typeIdx) implements ReferenceInstr {}
	public record Struct_New_Default(TypeIdx typeIdx) implements ReferenceInstr {}
	public record Struct_Get(TypeIdx typeIdx, FieldIdx fieldIdx) implements ReferenceInstr {}
	public record Struct_Get_S(TypeIdx typeIdx, FieldIdx fieldIdx) implements ReferenceInstr {}
	public record Struct_Get_U(TypeIdx typeIdx, FieldIdx fieldIdx) implements ReferenceInstr {}
	public record Struct_Set(TypeIdx typeIdx, FieldIdx fieldIdx) implements ReferenceInstr {}


	public record Array_New(TypeIdx typeIdx) implements ReferenceInstr {}
	public record Array_New_Fixed(TypeIdx typeIdx, int size) implements ReferenceInstr {}
	public record Array_New_Default(TypeIdx typeIdx) implements ReferenceInstr {}
	public record Array_New_Data(TypeIdx typeIdx, DataIdx dataIdx) implements ReferenceInstr {}
	public record Array_New_Elem(TypeIdx typeIdx, ElemIdx elemIdx) implements ReferenceInstr {}
	public record Array_Get(TypeIdx typeIdx) implements ReferenceInstr {}
	public record Array_Get_S(TypeIdx typeIdx) implements ReferenceInstr {}
	public record Array_Get_U(TypeIdx typeIdx) implements ReferenceInstr {}
	public record Array_Set(TypeIdx typeIdx) implements ReferenceInstr {}
	public record Array_Len() implements ReferenceInstr {}
	public record Array_Fill(TypeIdx typeIdx) implements ReferenceInstr {}
	public record Array_Copy(TypeIdx dest, TypeIdx src) implements ReferenceInstr {}
	public record Array_Init_Data(TypeIdx typeIdx, DataIdx dataIdx) implements ReferenceInstr {}
	public record Array_Init_Elem(TypeIdx typeIdx, ElemIdx elemIdx) implements ReferenceInstr {}


	public record I31_Get_S() implements ReferenceInstr {}
	public record I31_Get_U() implements ReferenceInstr {}
	public record Any_Convert_Extern() implements ReferenceInstr {}
	public record Extern_Convert_Any() implements ReferenceInstr {}
}
