package dev.argon.jawawasm.format.wast;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.collect.ImmutableList;
import org.jspecify.annotations.Nullable;

/**
 * An invocation or global read performed by a WAST script.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({@JsonSubTypes.Type(value = WastAction.Invoke.class, name = "invoke"),
		@JsonSubTypes.Type(value = WastAction.Get.class, name = "get"),})
public sealed interface WastAction {
	/**
	 * Invokes an exported function.
	 *
	 * @param module
	 *            The module identifier, or null for the current module.
	 * @param field
	 *            The exported function name.
	 * @param args
	 *            The argument values.
	 */
	public record Invoke(
		@Nullable String module,
		@JsonProperty(required = true) String field,
		@JsonProperty(required = true) ImmutableList<WastValue> args
	) implements WastAction {}

	/**
	 * Reads an exported global.
	 *
	 * @param module
	 *            The module identifier, or null for the current module.
	 * @param field
	 *            The exported global name.
	 */
	public record Get(@Nullable String module, @JsonProperty(required = true) String field) implements WastAction {}
}
