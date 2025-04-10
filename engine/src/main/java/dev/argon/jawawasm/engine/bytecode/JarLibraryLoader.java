package dev.argon.jawawasm.engine.bytecode;

import com.google.common.io.ByteStreams;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

class JarLibraryLoader implements LibraryLoader {
	public JarLibraryLoader(ZipFile jar, int release) {
		this.jar = jar;
		this.release = release;
	}

	public JarLibraryLoader(Path jarPath, int release) throws IOException {
		this(ZipFile.builder().setSeekableByteChannel(Files.newByteChannel(jarPath)).get(), release);
	}

	private final ZipFile jar;
	private final int release;


	private static final Pattern MULTI_RELEASE_PATTERN =
		Pattern.compile("META-INF/versions/(\\d+)/(.*)\\.class");

	@Override
	public Stream<JavaClass> allClasses() {
		Map<String, JavaClass> classFiles = new HashMap<>();
		Map<String, Integer> classVersions = new HashMap<>();

		Enumeration<ZipArchiveEntry> entries = jar.getEntries();

		while(entries.hasMoreElements()) {
			ZipArchiveEntry entry = entries.nextElement();
			String name = entry.getName();

			if(name.equals("module-info.class")) {
				continue;
			}

			if(!name.endsWith(".class")) {
				continue;
			}


			Matcher matcher = MULTI_RELEASE_PATTERN.matcher(name);
			String className;
			int version = 8;

			if(matcher.matches()) {
				version = Integer.parseInt(matcher.group(1));
				if(version > release || version < 9) continue; // Ignore future versions
				className = matcher.group(2);
			}
			else {
				className = name.substring(0, name.length() - 6);
			}

			// Keep only the highest version up to currentRelease
			if (!classVersions.containsKey(className) || classVersions.get(className) < version) {
				classFiles.put(className, new JarClass(className, entry));
				classVersions.put(className, version);
			}
		}

		return classFiles.values().stream();
	}

	@Override
	public void close() throws IOException {
		jar.close();
	}

	private class JarClass implements JavaClass {
		public JarClass(String className, ZipArchiveEntry entry) {
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
			return jar.getInputStream(entry);
		}
	}
}
