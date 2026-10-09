package dev.argon.jawawasm.format.wast;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.google.common.collect.ImmutableList;
import org.jspecify.annotations.Nullable;

/**
 * A command or assertion in a WAST script.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({@JsonSubTypes.Type(value = WastCommand.Module.class, name = "module"),
		@JsonSubTypes.Type(value = WastCommand.ModuleDefinition.class, name = "module_definition"),
		@JsonSubTypes.Type(value = WastCommand.ModuleInstance.class, name = "module_instance"),
		@JsonSubTypes.Type(value = WastCommand.Register.class, name = "register"),
		@JsonSubTypes.Type(value = WastCommand.Action.class, name = "action"),
		@JsonSubTypes.Type(value = WastCommand.AssertReturn.class, name = "assert_return"),
		@JsonSubTypes.Type(value = WastCommand.AssertException.class, name = "assert_exception"),
		@JsonSubTypes.Type(value = WastCommand.AssertTrap.class, name = "assert_trap"),
		@JsonSubTypes.Type(value = WastCommand.AssertExhaustion.class, name = "assert_exhaustion"),
		@JsonSubTypes.Type(value = WastCommand.AssertMalformed.class, name = "assert_malformed"),
		@JsonSubTypes.Type(value = WastCommand.AssertUnlinkable.class, name = "assert_unlinkable"),
		@JsonSubTypes.Type(value = WastCommand.AssertInvalid.class, name = "assert_invalid"),
		@JsonSubTypes.Type(value = WastCommand.AssertUninstantiable.class, name = "assert_uninstantiable"),})
public sealed interface WastCommand {
	/**
	 * Gets the source line of this command.
	 *
	 * @return The one-based source line.
	 */
	int line();

	/**
	 * Defines and instantiates a module.
	 *
	 * @param line
	 *            The one-based source line of the command.
	 * @param name
	 *            The module identifier, or null for an unnamed module.
	 * @param file
	 *            The file containing the module.
	 */
	public record Module(@JsonProperty(required = true) int line, @Nullable String name, @JsonUnwrapped WastFile file)
		implements
			WastCommand {}

	/**
	 * Defines a module without instantiating it.
	 *
	 * @param line
	 *            The one-based source line of the command.
	 * @param name
	 *            The module identifier, or null for an unnamed module.
	 * @param file
	 *            The file containing the module.
	 */
	public record ModuleDefinition(
		@JsonProperty(required = true) int line,
		@Nullable String name,
		@JsonUnwrapped WastFile file
	) implements WastCommand {}

	/**
	 * Instantiates a previously defined module.
	 *
	 * @param line
	 *            The one-based source line of the command.
	 * @param instance
	 *            The instance identifier, or null for an unnamed instance.
	 * @param module
	 *            The module identifier, or null for the current module.
	 */
	public record ModuleInstance(
		@JsonProperty(required = true) int line,
		@Nullable String instance,
		@Nullable String module
	) implements WastCommand {}

	/**
	 * Registers a module for subsequent imports.
	 *
	 * @param line
	 *            The one-based source line of the command.
	 * @param name
	 *            The module identifier, or null for an unnamed module.
	 * @param as
	 *            The name under which the module is registered.
	 */
	public record Register(
		@JsonProperty(required = true) int line,
		@Nullable String name,
		@JsonProperty(required = true) String as
	) implements WastCommand {}

	/**
	 * Executes an action without asserting its result.
	 *
	 * @param line
	 *            The one-based source line of the command.
	 * @param action
	 *            The action to execute.
	 */
	public record Action(
		@JsonProperty(required = true) int line,

		@JsonProperty(required = true) WastAction action
	) implements WastCommand {}

	/**
	 * Asserts that an action returns the expected values.
	 *
	 * @param line
	 *            The one-based source line of the command.
	 * @param action
	 *            The action to execute.
	 * @param expected
	 *            The expected result values.
	 */
	public record AssertReturn(
		@JsonProperty(required = true) int line,
		@JsonProperty(required = true) WastAction action,
		@JsonProperty(required = true) ImmutableList<WastValue> expected
	) implements WastCommand {}

	/**
	 * Asserts that an action throws a WebAssembly exception.
	 *
	 * @param line
	 *            The one-based source line of the command.
	 * @param action
	 *            The action to execute.
	 */
	public record AssertException(
		@JsonProperty(required = true) int line,
		@JsonProperty(required = true) WastAction action
	) implements WastCommand {}

	/**
	 * Asserts that an action traps.
	 *
	 * @param line
	 *            The one-based source line of the command.
	 * @param action
	 *            The action to execute.
	 * @param text
	 *            The expected diagnostic message.
	 */
	public record AssertTrap(
		@JsonProperty(required = true) int line,
		@JsonProperty(required = true) WastAction action,
		@JsonProperty(required = true) String text
	) implements WastCommand {}

	/**
	 * Asserts that an action exhausts runtime resources.
	 *
	 * @param line
	 *            The one-based source line of the command.
	 * @param action
	 *            The action to execute.
	 * @param text
	 *            The expected diagnostic message.
	 */
	public record AssertExhaustion(
		@JsonProperty(required = true) int line,
		@JsonProperty(required = true) WastAction action,
		@JsonProperty(required = true) String text
	) implements WastCommand {}

	/**
	 * Asserts that a module traps during instantiation.
	 *
	 * @param line
	 *            The one-based source line of the command.
	 * @param file
	 *            The file containing the module.
	 * @param text
	 *            The expected diagnostic message.
	 */
	public record AssertUninstantiable(
		@JsonProperty(required = true) int line,
		@JsonUnwrapped WastFile file,
		@JsonProperty(required = true) String text
	) implements WastCommand {}

	/**
	 * Asserts that a module cannot be decoded or parsed.
	 *
	 * @param line
	 *            The one-based source line of the command.
	 * @param file
	 *            The file containing the module.
	 * @param text
	 *            The expected diagnostic message.
	 */
	public record AssertMalformed(
		@JsonProperty(required = true) int line,
		@JsonUnwrapped WastFile file,
		@JsonProperty(required = true) String text
	) implements WastCommand {}

	/**
	 * Asserts that a module cannot be linked to its imports.
	 *
	 * @param line
	 *            The one-based source line of the command.
	 * @param file
	 *            The file containing the module.
	 * @param text
	 *            The expected diagnostic message.
	 */
	public record AssertUnlinkable(
		@JsonProperty(required = true) int line,
		@JsonUnwrapped WastFile file,
		@JsonProperty(required = true) String text
	) implements WastCommand {}

	/**
	 * Asserts that a decoded module fails validation.
	 *
	 * @param line
	 *            The one-based source line of the command.
	 * @param file
	 *            The file containing the module.
	 * @param text
	 *            The expected diagnostic message.
	 */
	public record AssertInvalid(@JsonProperty(required = true) int line, @JsonUnwrapped WastFile file, String text)
		implements
			WastCommand {}
}
