package dev.argon.jawawasm.format.modules;

import com.google.common.collect.ImmutableList;
import dev.argon.jawawasm.format.types.CompositeType;
import dev.argon.jawawasm.format.types.DefType;
import dev.argon.jawawasm.format.types.RecursiveType;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A WebAssembly module.
 * @param types The type section.
 * @param funcs The func section.
 * @param tables The table section.
 * @param mems The memory section.
 * @param tags The tags section.
 * @param globals The global section.
 * @param elems The elems section.
 * @param datas The data section.
 * @param start The start section.
 * @param imports The imports section.
 * @param exports The exports section.
 */
public record Module(
    ImmutableList<RecursiveType> types,
	ImmutableList<Func> funcs,
	ImmutableList<Table> tables,
	ImmutableList<Mem> mems,
	ImmutableList<Tag> tags,
	ImmutableList<Global> globals,
	ImmutableList<Elem> elems,
	ImmutableList<Data> datas,
	@Nullable Start start,
	ImmutableList<Import> imports,
	ImmutableList<Export> exports
) {
}
