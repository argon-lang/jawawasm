package dev.argon.jawawasm.format.wast;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * A generated module file referenced by a script command.
 *
 * @param filename
 *            The key of the file in WastScriptLoaded.getFiles().
 * @param moduleType
 *            The encoding of the file contents.
 * @param binaryFilename
 *            The key of a binary alternative for a text module, or null if
 *            absent.
 */
public record WastFile(
	@JsonProperty(required = true) String filename,
	@JsonProperty(value = "module_type", required = true) ModuleType moduleType,
	@JsonProperty(value = "binary_filename") @Nullable String binaryFilename
) {}
