package dev.argon.jvmwasm.format.modules;

/**
 * An export descriptor.
 */
public sealed interface ExportDesc {
	/**
	 * A function export.
	 * @param func The function.
	 */
	public static record Func(FuncIdx func) implements ExportDesc {}

	/**
	 * A table export.
	 * @param table The table.
	 */
	public static record Table(TableIdx table) implements ExportDesc {}

	/**
	 * A memory export.
	 * @param mem The memory.
	 */
	public static record Mem(MemIdx mem) implements ExportDesc {}

	/**
	 * A global export.
	 * @param global The global.
	 */
	public static record Global(GlobalIdx global) implements ExportDesc {}
}
