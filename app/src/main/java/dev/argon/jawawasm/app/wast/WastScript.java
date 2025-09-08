package dev.argon.jawawasm.app.wast;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.google.common.collect.ImmutableList;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record WastScript(
	@JsonProperty(required = true)
	String sourceFilename,

	@JsonProperty(required = true)
	ImmutableList<WastCommand> commands
) {
}
