package dev.argon.jawawasm.engine;

import dev.argon.jawawasm.runtime.ModuleResolutionException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Resolves modules registered by their import names. */
public final class MappedResolver<Mod> implements ModuleResolver<Mod> {
	private final Map<String, Mod> nameToRealization = new ConcurrentHashMap<>();

	/** Registers a module under an import name. */
	public void put(String name, Mod module) {
		nameToRealization.put(name, module);
	}

	@Override
	public Mod resolve(String name) throws ModuleResolutionException {
		var module = nameToRealization.get(name);
		if(module == null) {
			throw new ModuleResolutionException("Unknown module: " + name);
		}
		return module;
	}
}
