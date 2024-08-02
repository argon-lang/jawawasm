package jvmwasm.format.modules;

import jvmwasm.format.types.GlobalType;
import jvmwasm.format.types.MemType;
import jvmwasm.format.types.TableType;

public sealed interface ImportDesc {
	public static record Func(TypeIdx type) implements ImportDesc {}
	public static record Table(TableType type) implements ImportDesc {}
	public static record Mem(MemType type) implements ImportDesc {}
	public static record Global(GlobalType type) implements ImportDesc {}
}
