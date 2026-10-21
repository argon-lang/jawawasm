package dev.argon.jawawasm.gradle;

import com.google.common.collect.ImmutableList;
import dev.argon.jawawasm.engine.MappedResolver;
import dev.argon.jawawasm.engine.aot.AOTEngine;
import dev.argon.jawawasm.engine.compiler.WasmModuleRealization;
import dev.argon.jawawasm.engine.validator.ModuleValidator;
import dev.argon.jawawasm.format.binary.ModuleReader;
import dev.argon.jawawasm.runtime.ResultVoid;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.ListProperty;
import dev.argon.jawawasm.format.modules.Module;
import java.util.LinkedHashMap;
import java.util.HashSet;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import javax.inject.Inject;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Compiles a set of binary WebAssembly modules to JVM classes and resources.
 */
@CacheableTask
public abstract class WasmCompile extends DefaultTask {
	/**
	 * Creates a shared AOT compilation task.
	 */
	public WasmCompile() {}

	/**
	 * Lists the Wasm modules compiled by one engine.
	 *
	 * @return The module configuration list.
	 */
	@Nested
	public abstract ListProperty<WasmModule> getModules();

	/**
	 * Configures the package for generated classes.
	 *
	 * @return The Java package name property.
	 */
	@Input
	public abstract Property<String> getPackageName();

	/**
	 * Lists the Java hosts available during compilation.
	 *
	 * @return The host configuration list.
	 */
	@Nested
	public abstract ListProperty<HostModule> getHostModules();

	/**
	 * Configures Java class lookup during compilation.
	 *
	 * @return The host and dependency classpath.
	 */
	@Classpath
	public abstract ConfigurableFileCollection getClasspath();

	/**
	 * Configures the directory for generated classes and resources.
	 *
	 * @return The output directory property; its contents are replaced on
	 *         compilation.
	 */
	@OutputDirectory
	public abstract DirectoryProperty getOutputDirectory();

	/**
	 * Provides Gradle file operations for replacing generated output.
	 *
	 * @return The injected file-system service.
	 */
	@Inject
	@SuppressWarnings("JavaxInjectOnAbstractMethod") // Gradle implements injected service getters.
	protected abstract FileSystemOperations getFileSystemOperations();

	/**
	 * Validates and emits all modules in import order using one AOT engine.
	 *
	 * @throws Exception
	 *             If input parsing, validation, import resolution, or output
	 *             generation fails.
	 */
	@TaskAction
	public void compile() throws Exception {
		var output = getOutputDirectory().get().getAsFile();
		getFileSystemOperations().delete(spec -> spec.delete(output));
		var classpath = ImmutableList.<Path>builder();
		getClasspath().getFiles().stream().filter(file -> file.exists()).forEach(file -> classpath.add(file.toPath()));
		classpath.add(
			Path.of(
				Objects.requireNonNull(
					Objects.requireNonNull(ResultVoid.class.getProtectionDomain().getCodeSource()).getLocation()
				).toURI()
			)
		);
		var engine = new AOTEngine(getPackageName().get(), classpath.build(), output.toPath());
		var realized = new java.util.IdentityHashMap<ModuleReference, WasmModuleRealization>();
		for(var host : getHostModules().get()) {
			realized.put(host, engine.addHostModule(ClassDesc.of(host.getClassName().get())));
		}
		var pending = new LinkedHashMap<WasmModule, Module>();
		var classNames = new HashSet<String>();
		for(var entry : getModules().get()) {
			if(!classNames.add(entry.getClassName().get())) {
				throw new IllegalArgumentException("Duplicate generated class name: " + entry.getClassName().get());
			}
			try(var input = Files.newInputStream(entry.getSource().get().getAsFile().toPath())) {
				var module = new ModuleReader(input).readModule();
				ModuleValidator.validateModule(module);
				pending.put(entry, module);
			}
		}
		for(var entry : pending.entrySet()) {
			var configuration = entry.getKey();
			for(var target : configuration.getImports().get().values()) {
				if(!pending.containsKey(target) && !realized.containsKey(target)) {
					throw new IllegalArgumentException(
						"Unregistered import target '" + target.getName() + "' for module " + configuration.getName()
					);
				}
			}
			for(var imp : entry.getValue().imports()) {
				if(!configuration.getImports().get().containsKey(imp.module())) {
					throw new IllegalArgumentException(
						"No mapping configured for import '" + imp.module() + "' in module " + configuration.getName()
					);
				}
			}
		}
		while (!pending.isEmpty()) {
			boolean progressed = false;
			var iterator = pending.entrySet().iterator();
			while (iterator.hasNext()) {
				var entry = iterator.next();
				var configuration = entry.getKey();
				if(configuration.getImports().get().values().stream().anyMatch(pending::containsKey)) {
					continue;
				}
				var resolver = new MappedResolver<WasmModuleRealization>();
				configuration.getImports().get()
					.forEach((name, target) -> resolver.put(name, Objects.requireNonNull(realized.get(target))));
				var realization = engine.emitModule(entry.getValue(), configuration.getClassName().get(), resolver);
				realized.put(configuration, realization);
				iterator.remove();
				progressed = true;
			}
			if(!progressed) {
				throw new IllegalArgumentException(
					"Cyclic WebAssembly module imports: " + pending.keySet().stream().map(WasmModule::getName).toList()
				);
			}
		}
	}
}
