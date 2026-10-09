package dev.argon.jawawasm.engine.aot;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import dev.argon.jawawasm.engine.compiler.ModuleCompiler;
import dev.argon.jawawasm.format.ModuleFormatException;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

final class AOTClasspathLookup {
	private AOTClasspathLookup() {}

	public static @Nullable ClassModel loadClassModel(
		ClassFile classFile,
		ImmutableList<Path> classPath,
		ClassDesc classDesc
	) throws IOException {
		for(var path : classPath) {
			if(Files.isDirectory(path)) {
				var parts = Splitter.on('.').split(classDesc.packageName());
				Path dir = path;
				for(String part : parts) {
					dir = dir.resolve(part);
				}

				var classFilePath = dir.resolve(classDesc.displayName() + ".class");
				if(Files.exists(classFilePath)) {
					return classFile.parse(classFilePath);
				}
			}
			else {
				try(var zipFile = new ZipFile(path.toFile())) {
					String entryName;
					if(classDesc.packageName().isEmpty()) {
						entryName = classDesc.displayName() + ".class";
					}
					else {
						entryName = classDesc.packageName().replace('.', '/') + "/" + classDesc.displayName()
							+ ".class";
					}

					var classFileEntry = zipFile.getEntry(entryName);
					if(classFileEntry != null) {
						try(var is = zipFile.getInputStream(classFileEntry)) {
							var data = IOUtils.toByteArray(is);
							return classFile.parse(data);
						}
					}
				}
			}
		}

		return null;
	}

}
