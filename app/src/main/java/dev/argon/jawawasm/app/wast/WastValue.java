package dev.argon.jawawasm.app.wast;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.collect.ImmutableList;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;

@JsonTypeInfo(
	use = JsonTypeInfo.Id.NAME,
	include = JsonTypeInfo.As.EXISTING_PROPERTY,
	property = "type"
)
@JsonSubTypes({
	@JsonSubTypes.Type(value = WastValue.I32.class, name = "i32"),
	@JsonSubTypes.Type(value = WastValue.I64.class, name = "i64"),
	@JsonSubTypes.Type(value = WastValue.F32.class, name = "f32"),
	@JsonSubTypes.Type(value = WastValue.F64.class, name = "f64"),
	@JsonSubTypes.Type(value = WastValue.V128.class, name = "v128"),
	@JsonSubTypes.Type(value = WastValue.ExternRef.class, name = "externref"),
	@JsonSubTypes.Type(value = WastValue.FuncRef.class, name = "funcref"),
	@JsonSubTypes.Type(value = WastValue.AnyRef.class, name = "anyref"),
	@JsonSubTypes.Type(value = WastValue.ExnRef.class, name = "exnref"),
	@JsonSubTypes.Type(value = WastValue.I31Ref.class, name = "i31ref"),
	@JsonSubTypes.Type(value = WastValue.EqRef.class, name = "eqref"),
	@JsonSubTypes.Type(value = WastValue.ArrayRef.class, name = "arrayref"),
	@JsonSubTypes.Type(value = WastValue.StructRef.class, name = "structref"),
	@JsonSubTypes.Type(value = WastValue.NullRef.class, name = "nullref"),
	@JsonSubTypes.Type(value = WastValue.NullFuncRef.class, name = "nullfuncref"),
	@JsonSubTypes.Type(value = WastValue.NullExternRef.class, name = "nullexternref"),
	@JsonSubTypes.Type(value = WastValue.NullExnRef.class, name = "nullexnref"),
	@JsonSubTypes.Type(value = WastValue.RefNull.class, name = "refnull"),
	@JsonSubTypes.Type(value = WastValue.Either.class, name = "either"),
})
public sealed interface WastValue {
	public record I32(
		@JsonProperty(required = true)
		BigInteger value
	) implements WastValue {}
	public record I64(
		@JsonProperty(required = true)
		BigInteger value
	) implements WastValue {}
	public record F32(
		@JsonProperty(required = true)
		String value
	) implements WastValue {
		@Override
		public String toString() {
			Float floatValue;
			try {
				floatValue = Float.intBitsToFloat(new BigInteger(value).intValue());
			}
			catch(NumberFormatException _) {
				floatValue = null;
			}
			return "F32[value = " + value + ", float = " + floatValue + "]";
		}
	}
	public record F64(
		@JsonProperty(required = true)
		String value
	) implements WastValue {
		@Override
		public String toString() {
			Double floatValue;
			try {
				floatValue = Double.longBitsToDouble(new BigInteger(value).longValue());
			}
			catch(NumberFormatException _) {
				floatValue = null;
			}
			return "F64[value = " + value + ", float = " + floatValue + "]";
		}
	}

	public record V128(
		@JsonProperty(value = "lane_type", required = true)
		LaneType laneType,
		@JsonProperty(required = true)
		List<String> value
	) implements WastValue {}

	public enum LaneType {
		@JsonProperty("i8")
		I8,
		@JsonProperty("i16")
		I16,
		@JsonProperty("i32")
		I32,
		@JsonProperty("i64")
		I64,
		@JsonProperty("f32")
		F32,
		@JsonProperty("f64")
		F64,
	}

	public record ExternRef(
		@Nullable String value
	) implements WastValue { }

	public record FuncRef(
		@Nullable String value
	) implements WastValue { }

	public record AnyRef(
		@Nullable String value
	) implements WastValue { }

	public record ExnRef(
		@Nullable String value
	) implements WastValue { }

	public record I31Ref() implements WastValue { }


	public record EqRef() implements WastValue { }
	public record ArrayRef() implements WastValue { }
	public record StructRef() implements WastValue { }

	public record NullRef() implements WastValue { }
	public record NullFuncRef() implements WastValue { }
	public record NullExternRef() implements WastValue { }
	public record NullExnRef() implements WastValue { }
	public record RefNull() implements WastValue { }


	public record Either(
		@JsonProperty(required = true)
		ImmutableList<WastValue> values
	) implements WastValue { }
}
