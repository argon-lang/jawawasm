package dev.argon.jawawasm.app.wast;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.collect.ImmutableList;
import org.jspecify.annotations.Nullable;

@JsonTypeInfo(
	use = JsonTypeInfo.Id.NAME,
	include = JsonTypeInfo.As.EXISTING_PROPERTY,
	property = "type"
)
@JsonSubTypes({
	@JsonSubTypes.Type(value = WastAction.Invoke.class, name = "invoke"),
	@JsonSubTypes.Type(value = WastAction.Get.class, name = "get"),
})
public sealed interface WastAction {
	public record Invoke(
		@Nullable String module,
		@JsonProperty(required = true)
		String field,
		@JsonProperty(required = true)
		ImmutableList<WastValue> args
	) implements WastAction {}

	public record Get(
		@Nullable String module,
		@JsonProperty(required = true)
		String field
	) implements WastAction { }
}
