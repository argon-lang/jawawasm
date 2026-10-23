package dev.argon.jawawasm.reflection.generator;

import dev.argon.jawawasm.format.text.ModuleReader;
import java.nio.file.Files;
import java.nio.file.Path;

/** Builds the reflection driver's binary module. */
@org.jspecify.annotations.NullMarked
public final class CompileWat {
	private CompileWat() {}

	/** Converts the input path to the output path. */
	public static void main(String[] args) throws Exception {
		var output = Path.of(args[1]);
		Files.createDirectories(java.util.Objects.requireNonNull(output.toAbsolutePath().getParent()));
		try(var reader = Files.newBufferedReader(Path.of(args[0])); var stream = Files.newOutputStream(output)) {
			new ModuleReader(reader).readModuleAsBinary(stream);
		}
	}
}
