package dev.argon.jawawasm.engine.bytecode;

import com.google.common.io.ByteStreams;
import com.google.errorprone.annotations.MustBeClosed;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

class DirectoryLibraryLoader implements LibraryLoader {
	public DirectoryLibraryLoader(Path directory) {
		this.directory = directory;
	}

	private final Path directory;

	@Override
	@MustBeClosed
	public Stream<JavaClass> allClasses() {
		try {
			return Files.walk(directory)
				.filter(p ->
					Files.isRegularFile(p) &&
						com.google.common.io.Files.getFileExtension(p.getFileName().toString()).equals("class") &&
						!directory.relativize(p).toString().equals("module-info.class")
				)
				.map(JavaClassFile::new);
		}
		catch(IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void close() throws IOException {}

	private class JavaClassFile implements JavaClass {
		public JavaClassFile(Path path) {
			this.path = path;
		}

		private final Path path;

		@Override
		public ClassDesc descriptor() {
			return ClassDesc.ofInternalName(com.google.common.io.Files.getNameWithoutExtension(directory.relativize(path).toString()));
		}

		@Override
		public InputStream read() throws IOException {
			return Files.newInputStream(path);
		}
	}
}
