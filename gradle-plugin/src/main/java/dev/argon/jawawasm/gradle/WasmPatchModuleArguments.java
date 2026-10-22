package dev.argon.jawawasm.gradle;

import com.sun.source.util.JavacTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.CommandLineArgumentProvider;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;

/** Adds generated Wasm classes to the module being compiled by javac. */
public abstract class WasmPatchModuleArguments implements CommandLineArgumentProvider {
	/** Creates the argument provider. */
	public WasmPatchModuleArguments() {}

	/** {@return Whether Java module path inference is enabled.} */
	@Input
	public abstract Property<Boolean> getInferModulePath();

	/** {@return Module descriptors from the main Java sources.} */
	@InputFiles
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract ConfigurableFileCollection getModuleDescriptors();

	/** {@return The generated Wasm classes to patch into the main module.} */
	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract DirectoryProperty getGeneratedClasses();

	@Override
	public Iterable<String> asArguments() {
		if(!getInferModulePath().get() || getModuleDescriptors().isEmpty()) {
			return List.of();
		}
		var compiler = Objects
			.requireNonNull(ToolProvider.getSystemJavaCompiler(), "A JDK is required to read module-info.java");
		try(var files = compiler.getStandardFileManager(null, null, null)) {
			var sources = files.getJavaFileObjectsFromFiles(getModuleDescriptors());
			var parser = (JavacTask)compiler.getTask(null, files, null, List.of("-proc:none"), null, sources);
			for(var unit : parser.parse()) {
				var module = unit.getModule();
				if(module != null) {
					return List.of(
						"--patch-module",
						module.getName() + "=" + getGeneratedClasses().get().getAsFile().getAbsolutePath()
					);
				}
			}
			return List.of();
		}
		catch(IOException ex) {
			throw new UncheckedIOException("Could not read Java module descriptor", ex);
		}
	}
}
