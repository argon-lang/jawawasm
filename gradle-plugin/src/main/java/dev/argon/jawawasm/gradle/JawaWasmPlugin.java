package dev.argon.jawawasm.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.compile.JavaCompile;

/** Registers a shared AOT compilation for all WebAssembly modules. */
public final class JawaWasmPlugin implements Plugin<Project> {
	/**
	 * Creates the JawaWasm Gradle plugin.
	 */
	public JawaWasmPlugin() {}

	@Override
	public void apply(Project project) {
		project.getPluginManager().apply(JavaPlugin.class);
		var sourceSets = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
		var host = sourceSets.create("wasmHost");
		var main = sourceSets.getByName("main");
		var compileClasspath = main.getCompileClasspath();
		host.setCompileClasspath(compileClasspath);
		// Recompile hosts with main's module descriptor to enforce module access rules.
		main.getJava().source(host.getJava());
		main.getResources().source(host.getResources());
		var extension = project.getExtensions().create("wasm", WasmExtension.class);
		extension.getClasspath().from(host.getOutput(), compileClasspath);
		extension.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("generated/wasm"));
		var compile = project.getTasks().register("compileWasm", WasmCompile.class, task -> {
			task.setGroup("build");
			task.setDescription("Compile all WebAssembly modules using one shared AOTEngine.");
			task.getPackageName().set(extension.getPackageName());
			task.getOutputDirectory().set(extension.getOutputDirectory());
			task.getClasspath().from(extension.getClasspath());
			task.getHostModules().set(project.provider(() -> extension.getHostModules().stream().toList()));
			task.getModules().set(project.provider(() -> extension.registeredModules().stream().toList()));
		});
		main.setCompileClasspath(main.getCompileClasspath().plus(project.files(compile)));
		((ConfigurableFileCollection)main.getOutput().getClassesDirs()).from(compile);
		project.getTasks().named(main.getCompileJavaTaskName(), JavaCompile.class).configure(task -> {
			var patch = project.getObjects().newInstance(WasmPatchModuleArguments.class);
			patch.getInferModulePath().set(task.getModularity().getInferModulePath());
			patch.getModuleDescriptors()
				.from(task.getSource().matching(pattern -> pattern.include("**/module-info.java")));
			patch.getGeneratedClasses().set(compile.flatMap(WasmCompile::getOutputDirectory));
			task.getOptions().getCompilerArgumentProviders().add(patch);
		});
	}
}
