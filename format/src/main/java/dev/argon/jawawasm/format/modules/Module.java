package dev.argon.jawawasm.format.modules;

import dev.argon.jawawasm.format.types.FuncType;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A WebAssembly module.
 * @param types The type section.
 * @param funcs The func section.
 * @param tables The table section.
 * @param mems The memory section.
 * @param globals The global section.
 * @param elems The elems section.
 * @param datas The data section.
 * @param start The start section.
 * @param imports The imports section.
 * @param exports The exports section.
 */
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
