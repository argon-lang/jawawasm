package dev.argon.jawawasm.engine;

import dev.argon.jawawasm.runtime.ModuleResolutionException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves modules registered by their import names.
 *
 * @param <Mod>
 *            The type of the modules to be resolved.
 */
public final class MappedResolver<Mod> implements ModuleResolver<Mod> {
	private final Map<String, Mod> nameToRealization = new ConcurrentHashMap<>();

	/** Creates a resolver with no registered modules. */
	public MappedResolver() {}

	/**
	 * Registers a module under an import name, replacing any existing registration.
	 *
	 * @param name
	 *            The import name of the module.
	 * @param module
	 *            The module to register.
	 */
	public void put(String name, Mod module) {
		nameToRealization.put(name, module);
	}

	/**
	 * Resolves a registered module by its import name.
	 *
	 * @param name
	 *            The import name of the module.
	 * @return The registered module.
	 * @throws ModuleResolutionException
	 *             if no module is registered under the given name.
	 */
	@Override
	public Mod resolve(String name) throws ModuleResolutionException {
		var module = nameToRealization.get(name);
		if(module == null) {
			throw new ModuleResolutionException("Unknown module: " + name);
		}
		return module;
	}
}
