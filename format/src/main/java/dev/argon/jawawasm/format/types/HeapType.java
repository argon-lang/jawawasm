package dev.argon.jawawasm.format.types;

import dev.argon.jawawasm.format.modules.TypeIdx;

public sealed interface HeapType
	permits TypeIdx, BotType, DefType, HeapType.AbstractHeapType, RecTypeIdx
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
		I31,
		STRUCT,
		ARRAY,
		EXN,
	}
}
