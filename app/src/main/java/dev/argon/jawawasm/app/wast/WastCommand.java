package dev.argon.jawawasm.app.wast;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.google.common.collect.ImmutableList;
import org.jspecify.annotations.Nullable;

@JsonTypeInfo(
	use = JsonTypeInfo.Id.NAME,
	include = JsonTypeInfo.As.EXISTING_PROPERTY,
	property = "type"
)
@JsonSubTypes({
	@JsonSubTypes.Type(value = WastCommand.Module.class, name = "module"),
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
	@JsonSubTypes.Type(value = WastCommand.AssertUninstantiable.class, name = "assert_uninstantiable"),
})
public sealed interface WastCommand {
	int line();

	public record Module(
		@JsonProperty(required = true)
		int line,
		@Nullable String name,
		@JsonUnwrapped
		WastFile file
	) implements WastCommand { }

	public record ModuleDefinition(
		@JsonProperty(required = true)
		int line,
		@Nullable String name,
		@JsonUnwrapped
		WastFile file
	) implements WastCommand { }

	public record ModuleInstance(
		@JsonProperty(required = true)
		int line,
		@Nullable String instance,
		@Nullable String module
	) implements WastCommand { }

	public record Register(
		@JsonProperty(required = true)
		int line,
		@Nullable String name,
		@JsonProperty(required = true)
		String as
	) implements WastCommand { }

	public record Action(
		@JsonProperty(required = true)
		int line,

		@JsonProperty(required = true)
		WastAction action
	) implements WastCommand { }

	public record AssertReturn(
		@JsonProperty(required = true)
		int line,
		@JsonProperty(required = true)
		WastAction action,
		@JsonProperty(required = true)
		ImmutableList<WastValue> expected
	) implements WastCommand {}

	public record AssertException(
		@JsonProperty(required = true)
		int line,
		@JsonProperty(required = true)
		WastAction action
	) implements WastCommand {}

	public record AssertTrap(
		@JsonProperty(required = true)
		int line,
		@JsonProperty(required = true)
		WastAction action,
		@JsonProperty(required = true)
		String text
	) implements WastCommand { }

	public record AssertExhaustion(
		@JsonProperty(required = true)
		int line,
		@JsonProperty(required = true)
		WastAction action,
		@JsonProperty(required = true)
		String text
	) implements WastCommand { }

	public record AssertUninstantiable(
		@JsonProperty(required = true)
		int line,
		@JsonUnwrapped
		WastFile file,
		@JsonProperty(required = true)
		String text
	) implements WastCommand { }

	public record AssertMalformed(
		@JsonProperty(required = true)
		int line,
		@JsonUnwrapped
		WastFile file,
		@JsonProperty(required = true)
		String text
	) implements WastCommand { }

	public record AssertUnlinkable(
		@JsonProperty(required = true)
		int line,
		@JsonUnwrapped
		WastFile file,
		@JsonProperty(required = true)
		String text
	) implements WastCommand { }

	public record AssertInvalid(
		@JsonProperty(required = true)
		int line,
		@JsonUnwrapped
		WastFile file,
		String text
	) implements WastCommand { }
}
