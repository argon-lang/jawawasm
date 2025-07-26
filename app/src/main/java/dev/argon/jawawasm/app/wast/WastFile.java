package dev.argon.jawawasm.app.wast;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

public record WastFile(
	@JsonProperty(required = true)
	String filename,
	@JsonProperty(value = "module_type", required = true)
	ModuleType moduleType,
	@JsonProperty(value = "binary_filename")
	@Nullable String binaryFilename
) {
}
