package dev.argon.jawawasm.app.wast;

import tools.jackson.databind.EnumNamingStrategies;
import tools.jackson.databind.annotation.EnumNaming;

@EnumNaming(EnumNamingStrategies.SnakeCaseStrategy.class)
public enum ModuleType {
	BINARY,
	TEXT,
}
