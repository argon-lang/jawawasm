package dev.argon.jawawasm.format.wast;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import com.google.common.collect.ImmutableList;

/**
 * The source metadata and commands of a parsed WAST script.
 *
 * @param sourceFilename
 *            The source filename used for diagnostics.
 * @param commands
 *            The commands in execution order.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record WastScript(
	@JsonProperty(required = true) String sourceFilename,

	@JsonProperty(required = true) ImmutableList<WastCommand> commands
) {}
