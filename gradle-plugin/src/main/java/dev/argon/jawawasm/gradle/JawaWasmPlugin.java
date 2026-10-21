package dev.argon.jawawasm.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.bundling.Jar;

/** Registers a shared AOT compilation and per-module entry points. */
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
		main.setCompileClasspath(compileClasspath.plus(host.getOutput()));
		main.setRuntimeClasspath(main.getRuntimeClasspath().plus(host.getOutput()));
		project.getTasks().named("jar", Jar.class).configure(task -> task.from(host.getOutput()));
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
		main.getOutput().dir(compile);
		extension.registeredModules().all(module -> {
			project.getTasks().register("compileWasm" + module.getName(), task -> {
				task.setGroup("build");
				task.setDescription("Compile the shared WebAssembly module set including " + module.getName() + ".");
				task.dependsOn(compile);
			});
		});
	}
}
