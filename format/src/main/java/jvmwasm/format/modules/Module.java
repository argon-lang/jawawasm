package jvmwasm.format.modules;

import jvmwasm.format.types.FuncType;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record Module(
    List<? extends FuncType> types,
    List<? extends Func> funcs,
    List<? extends Table> tables,
    List<? extends Mem> mems,
    List<? extends Global> globals,
    List<? extends Elem> elems,
    List<? extends Data> datas,
	@Nullable Start start,
    List<? extends Import> imports,
    List<? extends Export> exports
) {
}
