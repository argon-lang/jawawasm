package dev.argon.jawawasm.gradle;

import org.gradle.api.Named;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.*;

/**
 * One module in a shared AOT compilation.
 */
public abstract class WasmModule implements ModuleReference {
	private final String name;

	/**
	 * Creates a Wasm module configuration.
	 *
	 * @param name
	 *            The configuration entry name.
	 */
	@javax.inject.Inject
	@SuppressWarnings("InjectOnConstructorOfAbstractClass") // Gradle decorates and instantiates this type.
	public WasmModule(String name) {
		this.name = name;
	}

	@Override
	@Input
	public String getName() {
		return name;
	}

	/**
	 * Configures the binary Wasm input.
	 *
	 * @return The input file property, which may carry a producer task dependency.
	 */
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getSource();

	/**
	 * Configures the Java class name.
	 *
	 * @return The generated simple Java class name; defaults to the entry name.
	 */
	@Input
	public abstract Property<String> getClassName();

	/**
	 * Maps this module's Wasm import names to module or host objects.
	 *
	 * @return The explicit import mappings; keys are exact Wasm import strings.
	 */
	@Internal
	public abstract MapProperty<String, ModuleReference> getImports();

	/**
	 * Stable import identities for task input tracking without traversing the
	 * dependency graph.
	 *
	 * @return Import names mapped to host or Wasm configuration identities.
	 */
	@Input
	public java.util.Map<String, String> getImportTargets() {
		var targets = new java.util.TreeMap<String, String>();
		getImports().get().forEach(
			(name, target) -> targets.put(name, (target instanceof HostModule ? "host:" : "wasm:") + target.getName())
		);
		return targets;
	}
}
