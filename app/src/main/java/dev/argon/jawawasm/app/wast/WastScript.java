package dev.argon.jawawasm.app.wast;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

public record WastScript(
	@JsonProperty(value = "source_filename", required = true)
	String sourceFilename,

	@JsonProperty(required = true)
	ImmutableList<WastCommand> commands
) {
}
