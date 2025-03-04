package dev.argon.jawawasm.format.types;

import dev.argon.jawawasm.format.modules.TypeIdx;

public sealed interface HeapType
	permits TypeIdx, BotType, FuncType, HeapType.AbstractHeapType
{

	enum AbstractHeapType implements HeapType {
		FUNC,
		EXTERN,
		EXN,
	}
}
