package jvmwasm.format.modules;

public sealed interface ExportDesc {
	public static record Func(FuncIdx func) implements ExportDesc {}
	public static record Table(TableIdx table) implements ExportDesc {}
	public static record Mem(MemIdx mem) implements ExportDesc {}
	public static record Global(GlobalIdx global) implements ExportDesc {}
}
