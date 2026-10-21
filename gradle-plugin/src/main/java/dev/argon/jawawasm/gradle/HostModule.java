package dev.argon.jawawasm.gradle;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

/** A Java host module available to the shared AOT compiler. */
public abstract class HostModule implements ModuleReference {
	private final String name;

	/**
	 * Creates a host module configuration.
	 *
	 * @param name
	 *            The configuration entry name.
	 */
	@javax.inject.Inject
	@SuppressWarnings("InjectOnConstructorOfAbstractClass")
	public HostModule(String name) {
		this.name = name;
	}

	@Override
	@Input
	public String getName() {
		return name;
	}

	/**
	 * Configures the Java class name.
	 *
	 * @return The fully qualified Java host class name.
	 */
	@Input
	public abstract Property<String> getClassName();
}
