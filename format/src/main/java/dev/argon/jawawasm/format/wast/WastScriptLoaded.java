package dev.argon.jawawasm.format.wast;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * A parsed script and the in-memory files referenced by its commands.
 */
public class WastScriptLoaded {
	/**
	 * Creates a loaded script with its referenced files in memory.
	 *
	 * @param files
	 *            The generated module files keyed by their JSON filenames.
	 * @param script
	 *            The parsed source metadata and commands.
	 */
	public WastScriptLoaded(ImmutableMap<String, byte[]> files, WastScript script) {
		this.files = files;
		this.script = script;
	}

	private final ImmutableMap<String, byte[]> files;
	private final WastScript script;

	/**
	 * Gets the generated files by the filenames referenced in the JSON commands.
	 *
	 * @return The files, which may contain binary modules or quoted text.
	 */
	public ImmutableMap<String, byte[]> getFiles() {
		return files;
	}

	/**
	 * Gets the script commands.
	 *
	 * @return The parsed script.
	 */
	public WastScript getScript() {
		return script;
	}
}
