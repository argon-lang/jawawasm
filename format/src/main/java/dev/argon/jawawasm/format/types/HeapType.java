package dev.argon.jawawasm.format.types;

import dev.argon.jawawasm.format.modules.TypeIdx;

/**
 * Represents a heap type in WebAssembly.
 * A heap type defines various kinds of references that can be used in Wasm.
 */
public sealed interface HeapType
	permits TypeIdx, BotType, DefType, HeapType.AbstractHeapType, RecTypeIdx
{

	/**
	 * Represents abstract heap types in WebAssembly.
	 * These types define different categories of reference types.
	 */
	enum AbstractHeapType implements HeapType {
		/**
		 * No exception type.
		 */
		NOEXN,

		/**
		 * No function type.
		 */
		NOFUNC,

		/**
		 * No external type.
		 */
		NOEXTERN,

		/**
		 * Represents the absence of a heap type.
		 */
		NONE,

		/**
		 * Function reference type.
		 */
		FUNC,

		/**
		 * External reference type.
		 */
		EXTERN,

		/**
		 * Any reference type.
		 */
		ANY,

		/**
		 * Equality-comparable reference type.
		 */
		EQ,

		/**
		 * I31 reference type (31-bit integer representation).
		 */
		I31,

		/**
		 * Struct reference type.
		 */
		STRUCT,

		/**
		 * Array reference type.
		 */
		ARRAY,

		/**
		 * Exception reference type.
		 */
		EXN,
	}
}
