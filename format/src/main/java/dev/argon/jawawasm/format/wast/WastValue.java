package dev.argon.jawawasm.format.wast;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import tools.jackson.databind.EnumNamingStrategies;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.EnumNaming;
import tools.jackson.databind.annotation.JsonNaming;
import com.google.common.collect.ImmutableList;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;

/**
 * An argument value or expected result pattern in a WAST script.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({@JsonSubTypes.Type(value = WastValue.I32.class, name = "i32"),
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
		@JsonSubTypes.Type(value = WastValue.Either.class, name = "either"),})
public sealed interface WastValue {
	/**
	 * A 32-bit integer value.
	 *
	 * @param value
	 *            The integer represented by the JSON value string.
	 */
	public record I32(@JsonProperty(required = true) BigInteger value) implements WastValue {}
	/**
	 * A 64-bit integer value.
	 *
	 * @param value
	 *            The integer represented by the JSON value string.
	 */
	public record I64(@JsonProperty(required = true) BigInteger value) implements WastValue {}
	/**
	 * A 32-bit floating-point value or NaN result pattern.
	 *
	 * @param value
	 *            The decimal bit representation, or a NaN pattern such as
	 *            nan:canonical.
	 */
	public record F32(@JsonProperty(required = true) String value) implements WastValue {
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
	/**
	 * A 64-bit floating-point value or NaN result pattern.
	 *
	 * @param value
	 *            The decimal bit representation, or a NaN pattern such as
	 *            nan:canonical.
	 */
	public record F64(@JsonProperty(required = true) String value) implements WastValue {
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

	/**
	 * A SIMD value or result pattern described by its lanes.
	 *
	 * @param laneType
	 *            The lane interpretation.
	 * @param value
	 *            The lane values or floating-point result patterns.
	 */
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record V128(
		@JsonProperty(required = true) LaneType laneType,
		@JsonProperty(required = true) List<String> value
	) implements WastValue {}

	/**
	 * The scalar type used to interpret SIMD lanes.
	 */
	@EnumNaming(EnumNamingStrategies.SnakeCaseStrategy.class)
	public enum LaneType {
		/**
		 * Sixteen 8-bit integer lanes.
		 */
		I8,
		/**
		 * Eight 16-bit integer lanes.
		 */
		I16,
		/**
		 * Four 32-bit integer lanes.
		 */
		I32,
		/**
		 * Two 64-bit integer lanes.
		 */
		I64,
		/**
		 * Four 32-bit floating-point lanes.
		 */
		F32,
		/**
		 * Two 64-bit floating-point lanes.
		 */
		F64,
	}

	/**
	 * An external reference value or result pattern.
	 *
	 * @param value
	 *            The host reference identifier or null-reference marker, or null
	 *            for any external reference.
	 */
	public record ExternRef(@Nullable String value) implements WastValue {}

	/**
	 * A function reference result pattern.
	 *
	 * @param value
	 *            The reference descriptor, or null for any function reference.
	 */
	public record FuncRef(@Nullable String value) implements WastValue {}

	/**
	 * An internal reference value or result pattern.
	 *
	 * @param value
	 *            The reference descriptor, or null for any internal reference.
	 */
	public record AnyRef(@Nullable String value) implements WastValue {}

	/**
	 * An exception reference result pattern.
	 *
	 * @param value
	 *            The reference descriptor, or null for any exception reference.
	 */
	public record ExnRef(@Nullable String value) implements WastValue {}

	/**
	 * Matches an i31 reference.
	 */
	public record I31Ref() implements WastValue {}

	/**
	 * Matches an equality-comparable reference.
	 */
	public record EqRef() implements WastValue {}
	/**
	 * Matches an array reference.
	 */
	public record ArrayRef() implements WastValue {}
	/**
	 * Matches a struct reference.
	 */
	public record StructRef() implements WastValue {}

	/**
	 * Matches a null reference with bottom heap type none.
	 */
	public record NullRef() implements WastValue {}
	/**
	 * Matches a null function reference.
	 */
	public record NullFuncRef() implements WastValue {}
	/**
	 * Matches a null external reference.
	 */
	public record NullExternRef() implements WastValue {}
	/**
	 * Matches a null exception reference.
	 */
	public record NullExnRef() implements WastValue {}
	/**
	 * Matches a null reference regardless of heap type.
	 */
	public record RefNull() implements WastValue {}

	/**
	 * Matches any of several alternative result patterns.
	 *
	 * @param values
	 *            The allowed alternatives.
	 */
	public record Either(@JsonProperty(required = true) ImmutableList<WastValue> values) implements WastValue {}
}
