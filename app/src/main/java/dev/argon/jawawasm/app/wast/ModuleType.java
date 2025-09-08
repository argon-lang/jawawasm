package dev.argon.jawawasm.app.wast;

import com.fasterxml.jackson.databind.EnumNamingStrategies;
import com.fasterxml.jackson.databind.annotation.EnumNaming;

@EnumNaming(EnumNamingStrategies.SnakeCaseStrategy.class)
public enum ModuleType {
	BINARY,
	TEXT,
}
