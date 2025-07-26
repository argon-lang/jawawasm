package dev.argon.jawawasm.gradleplugin;

import dev.argon.jawawasm.engine.ModuleResolver;
import dev.argon.jawawasm.engine.annotationprocessor.JawaWasmHierarchyAnnotationProcessor;
import dev.argon.jawawasm.engine.annotationprocessor.JawaWasmLoaderAnnotationProcessor;
import dev.argon.jawawasm.engine.bytecode.BytecodeModuleLoader;
import dev.argon.jawawasm.engine.bytecode.LibraryLoader;
import dev.argon.jawawasm.engine.compiler.CompilerOptions;
import dev.argon.jawawasm.engine.compiler.ModuleCompiler;
import dev.argon.jawawasm.engine.compiler.WasmModuleRealization;
import dev.argon.jawawasm.format.binary.ModuleReader;
import dev.argon.jawawasm.runtime.ModuleResolutionException;
import org.gradle.api.DefaultTask;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import javax.annotation.processing.Processor;
import javax.tools.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class JawaWasmCompilerTask extends DefaultTask {

	private final Property<String> outputPackage = getProject().getObjects().property(String.class);
	private final DomainObjectSet<WasmModuleFile> inputModules = getProject().getObjects().domainObjectSet(WasmModuleFile.class);
	private final DirectoryProperty classOutputDir = getProject().getObjects().directoryProperty();
	private final Configuration classpath = getProject().getConfigurations().getByName("compileClasspath");

	@Input
	public Property<String> getOutputPackage() {
		return outputPackage;
	}

	@InputFiles
	public DomainObjectSet<WasmModuleFile> getInputModules() {
		return inputModules;
	}

	@OutputDirectory
	public DirectoryProperty getClassOutputDir() {
		return classOutputDir;
	}

	@Classpath
	public Configuration getClasspath() {
		return classpath;
	}

	@TaskAction
	public void generateClasses() throws Exception {
		var classOutputDir = getClassOutputDir().get().getAsFile().toPath();

		getProject().delete(classOutputDir);

		var hierarchyProcessor = new JawaWasmHierarchyAnnotationProcessor();
		runProcessor(hierarchyProcessor);
		var sourceResolver = hierarchyProcessor.hierarchyResolver();

		var loader = LibraryLoader.of(
			classpath.getFiles().stream().map(File::toPath).toList(),
			getProject().getExtensions().getByType(JavaPluginExtension.class).getToolchain().getLanguageVersion().get().asInt()
		);

		var options = new CompilerOptions(
			sourceResolver.orElse(loader.hierarchyResolver()),
			getOutputPackage().get()
		);

		var compiler = new ModuleCompiler(options);

		new BytecodeModuleLoader(compiler, loader).scanClasses();

		var loaderProcessor = new JawaWasmLoaderAnnotationProcessor(compiler);
		runProcessor(loaderProcessor);

		for(var inputModule : getInputModules()) {
			dev.argon.jawawasm.format.modules.Module module;
			try(var is = Files.newInputStream(inputModule.file().toPath())) {
				var reader = new ModuleReader(is);
				module = reader.readModule();
			}
			compiler.enqueueModule(module, inputModule.className(), )
		}

	}

	private static class WasmModuleResolver implements ModuleResolver<WasmModuleRealization> {


		@Override
		public WasmModuleRealization resolve(String name) throws ModuleResolutionException {
			return null;
		}
	}


	private void runProcessor(Processor processor) throws IOException {

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

		try(StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {

			List<File> javaSourceFiles = new ArrayList<>();

			var sourceSets = (SourceSetContainer) getProject().getExtensions().getByName("sourceSets");
			sourceSets.named("main", sourceSet -> {
				javaSourceFiles.addAll(sourceSet.getJava().getFiles());
			});

			var compilationUnits = fileManager.getJavaFileObjects(javaSourceFiles.toArray(File[]::new));

			JavaCompiler.CompilationTask task = compiler.getTask(
				Writer.nullWriter(),
				fileManager,
				diagnostics,
				List.of(
					"-proc:only",
					"--module-path",
					getClasspath()
						.filter(f -> !f.equals(classOutputDir.getAsFile()))
						.getAsPath()
				),
				null,
				compilationUnits
			);

			task.setProcessors(List.of(processor));
			task.call();
		}


	}


}
