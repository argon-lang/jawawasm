package dev.argon.jawawasm.app.wast;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ModuleType {
	@JsonProperty("binary")
	BINARY,

	@JsonProperty("text")
	TEXT,
}
