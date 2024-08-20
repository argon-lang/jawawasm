package dev.argon.jawawasm.format.types;

import dev.argon.jawawasm.format.modules.TypeIdx;

public sealed interface HeapType
	permits TypeIdx, BotType, FuncType, HeapType.Extern, HeapType.Func
{
	public record Func() implements HeapType {}
	public record Extern() implements HeapType {}
}
