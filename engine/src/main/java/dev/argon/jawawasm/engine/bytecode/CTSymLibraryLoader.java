package dev.argon.jawawasm.engine.bytecode;

import com.google.common.io.ByteStreams;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

final class CTSymLibraryLoader implements LibraryLoader, Closeable {
	public CTSymLibraryLoader(ZipFile ctSym, int release) {
		this.ctSym = ctSym;
		this.release = release;
	}

	public CTSymLibraryLoader(Path ctSymPath, int release) throws IOException {
		this(ZipFile.builder().setSeekableByteChannel(Files.newByteChannel(ctSymPath)).get(), release);
	}

	private final ZipFile ctSym;
	private final int release;

	@Override
	public Stream<JavaClass> allClasses() {
		char releaseChar = releaseVersionChar();

		return StreamSupport.stream(
			Spliterators.spliteratorUnknownSize(ctSym.getEntries().asIterator(), 0),
			false
		)
			.<JavaClass>map(entry -> {
				var name = entry.getName();

				int versionSlash = name.indexOf('/');
				if(versionSlash < 0) {
					return null;
				}

				if(name.indexOf(releaseChar, 0, versionSlash) < 0) {
					return null;
				}

				int moduleSlash = name.indexOf('/', versionSlash + 1);
				if(moduleSlash < 0) {
					return null;
				}

				if(!name.endsWith(".class")) {
					return null;
				}

				String className = name.substring(moduleSlash + 1, name.length() - 6);

				return new CTSymClass(className, entry);

			})
			.filter(Objects::nonNull);
	}

	private class CTSymClass implements JavaClass {
		public CTSymClass(String className, ZipArchiveEntry entry) {
			this.className = className;
			this.entry = entry;
		}

		private final String className;
		private final ZipArchiveEntry entry;

		@Override
		public ClassDesc descriptor() {
			return ClassDesc.ofInternalName(className);
		}

		@Override
		public InputStream read() throws IOException {
			return ctSym.getInputStream(entry);
		}
	}

	@Override
	public void close() throws IOException {
		ctSym.close();
	}

	private char releaseVersionChar() {
		if(release < 0) {
			throw new RuntimeException("Invalid JDK release version: " + release);
		}

		if(release < 10) {
			return (char)('0' + release);
		}
		else if(release < 36) {
			return (char)('A' + release);
		}
		else {
			throw new RuntimeException("Unsupported JDK release version: " + release);
		}
	}
}
