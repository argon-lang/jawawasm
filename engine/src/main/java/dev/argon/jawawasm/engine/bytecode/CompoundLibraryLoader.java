package dev.argon.jawawasm.engine.bytecode;

import com.google.common.collect.ImmutableList;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.util.stream.Stream;

final class CompoundLibraryLoader implements LibraryLoader {
	public CompoundLibraryLoader(ImmutableList<LibraryLoader> loaders) {
		this.loaders = loaders;
	}

	private final ImmutableList<LibraryLoader> loaders;

	@Override
	public Stream<JavaClass> allClasses() {
		return loaders.stream()
			.flatMap(LibraryLoader::allClasses);
	}

	@Override
	public void close() throws IOException {
		for(var loader : loaders) {
			loader.close();
		}
	}
}
