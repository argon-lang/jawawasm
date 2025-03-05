package dev.argon.jawawasm.format.types;

import dev.argon.jawawasm.format.modules.TypeIdx;

public sealed interface HeapType
	permits TypeIdx, BotType, FuncType, HeapType.AbstractHeapType
{

	enum AbstractHeapType implements HeapType {
		NOEXN,
		NOFUNC,
		NOEXTERN,
		NONE,
		FUNC,
		EXTERN,
		ANY,
		EQ,
		I32,
		STRUCT,
		ARRAY,
		EXN,
	}
}
