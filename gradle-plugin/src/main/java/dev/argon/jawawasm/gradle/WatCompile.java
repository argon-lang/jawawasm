package dev.argon.jawawasm.gradle;

import dev.argon.jawawasm.format.text.ModuleReader;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;

import java.io.IOException;
import java.nio.file.Files;
import dev.argon.jawawasm.format.ModuleFormatException;

/** Converts a WebAssembly text module to binary format. */
@CacheableTask
public abstract class WatCompile extends DefaultTask {

	/** Creates a text conversion task. */
	public WatCompile() {}

	/**
	 * Configures the input text module.
	 * 
	 * @return The input text module.
	 */
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getSource();

	/**
	 * Configures the output binary module.
	 * 
	 * @return The output binary module.
	 */
	@OutputFile
	public abstract RegularFileProperty getOutputFile();

	/**
	 * Converts the module.
	 * 
	 * @throws IOException
	 *             when reading or writing fails.
	 * @throws ModuleFormatException
	 *             when the text is invalid.
	 */
	@TaskAction
	public void compile() throws IOException, ModuleFormatException {
		var output = getOutputFile().get().getAsFile().toPath();
		Files.createDirectories(java.util.Objects.requireNonNull(output.toAbsolutePath().getParent()));
		try(var reader = Files.newBufferedReader(getSource().get().getAsFile().toPath());
			var stream = Files.newOutputStream(output)) {
			new ModuleReader(reader).readModuleAsBinary(stream);
		}
	}
}
