package dev.argon.jawawasm.gradle;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import javax.inject.Inject;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

/** Configuration shared by all modules compiled by one AOTEngine. */
public abstract class WasmExtension {
	private final ObjectFactory objects;
	private final DomainObjectSet<WasmModule> registeredModules;

	/**
	 * Creates the shared Wasm compiler configuration.
	 *
	 * @param objects
	 *            Gradle's object factory.
	 */
	@Inject
	@SuppressWarnings("InjectOnConstructorOfAbstractClass")
	public WasmExtension(ObjectFactory objects) {
		this.objects = objects;
		registeredModules = objects.domainObjectSet(WasmModule.class);
	}

	/**
	 * Creates and registers a module for the shared compilation.
	 *
	 * @param name
	 *            The unique configuration entry name.
	 * @return The module object, which can be used in import maps.
	 */
	public WasmModule module(String name) {
		return module(name, module -> {});
	}

	/**
	 * Creates, configures, and registers a module for the shared compilation.
	 *
	 * @param name
	 *            The unique configuration entry name.
	 * @param configure
	 *            The module configuration action.
	 * @return The configured module object, which can be used in import maps.
	 */
	public WasmModule module(String name, Action<? super WasmModule> configure) {
		if(registeredModules.stream().anyMatch(module -> module.getName().equals(name))) {
			throw new IllegalArgumentException("Duplicate Wasm module name: " + name);
		}
		var module = objects.newInstance(WasmModule.class, name);
		module.getClassName().convention(name);
		configure.execute(module);
		registeredModules.add(module);
		return module;
	}

	final DomainObjectSet<WasmModule> registeredModules() {
		return registeredModules;
	}

	/**
	 * Configures the package shared by generated classes.
	 *
	 * @return The required Java package name property.
	 */
	public abstract Property<String> getPackageName();

	/**
	 * Configures the directory replaced by AOT compilation.
	 *
	 * @return The output directory property, defaulting to
	 *         {@code build/generated/wasm}.
	 */
	public abstract DirectoryProperty getOutputDirectory();

	/**
	 * Configures the classpath used to look up Java hosts and types.
	 *
	 * @return The AOT classpath, including host output and the original main
	 *         compile classpath.
	 */
	public abstract ConfigurableFileCollection getClasspath();

	/**
	 * Configures Java host modules available to import maps.
	 *
	 * @return The named Java host module configurations.
	 */
	public abstract NamedDomainObjectContainer<HostModule> getHostModules();
}
