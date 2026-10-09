package dev.argon.jawawasm.format.wast;

import tools.jackson.databind.EnumNamingStrategies;
import tools.jackson.databind.annotation.EnumNaming;

/**
 * The encoding of a module file in a WAST script.
 */
@EnumNaming(EnumNamingStrategies.SnakeCaseStrategy.class)
public enum ModuleType {
	/**
	 * WebAssembly binary encoding.
	 */
	BINARY,
	/**
	 * WebAssembly text encoding.
	 */
	TEXT,
}
