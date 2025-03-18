package dev.argon.jawawasm.engine.interpreter;

import dev.argon.jawawasm.format.types.*;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A WebAssembly array.
 */
public sealed abstract class DynamicWasmArray implements DynamicWasmEq {
	private DynamicWasmArray() {}

	/**
	 * Get the length of the array.
	 * @return The length of the array.
	 */
	public abstract int length();

	/**
	 * Gets the element at index.
	 * @param index The index.
	 * @return The element at the index.
	 */
	public abstract @Nullable Object get(int index);

	/**
	 * Sets the element at index.
	 * @param index The index.
	 * @param value The new value.
	 */
	public abstract void set(int index, @Nullable Object value);

	static DynamicWasmArray create(DefType type, int length) {
		var arrayType = (ArrayType)type.recursiveType().subtypes().get(type.index()).compositeType();
		return switch(arrayType.fieldType().storageType()) {
			case PackedType packedType -> switch(packedType) {
				case I8 -> new OfByte(type, new byte[length]);
				case I16 -> new OfShort(type, new short[length]);
			};
			case NumType numType -> switch(numType) {
				case I32 -> new OfInt(type, new int[length]);
				case I64 -> new OfLong(type, new long[length]);
				case F32 -> new OfFloat(type, new float[length]);
				case F64 -> new OfDouble(type, new double[length]);
			};
			default -> new OfObject(type, new Object[length]);
		};
	}

	/**
	 * A WebAssembly array backed by objects.
	 */
	public static final class OfObject extends DynamicWasmArray {
		private OfObject(DefType type, Object[] values) {
			this.type = type;
			this.values = values;
		}

		private final DefType type;
		private final @Nullable Object[] values;

		@Override
		public HeapType heapType() {
			return type;
		}

		@Override
		public int length() {
			return values.length;
		}

		@Override
		public @Nullable Object get(int index) {
			return values[index];
		}

		@Override
		public void set(int index, @Nullable Object value) {
			values[index] = value;
		}
	}

	/**
	 * A WebAssembly array backed by bytes.
	 */
	public static final class OfByte extends DynamicWasmArray {
		OfByte(DefType type, byte[] values) {
			this.type = type;
			this.values = values;
		}

		private final DefType type;
		private final byte[] values;

		@Override
		public HeapType heapType() {
			return type;
		}

		@Override
		public int length() {
			return values.length;
		}

		@Override
		public Object get(int index) {
			return values[index];
		}

		@Override
		public void set(int index, @Nullable Object value) {
			Objects.requireNonNull(value);
			values[index] = (byte)value;
		}

		/**
		 * Gets a byte.
		 * @param index The index.
		 * @return The byte at the index.
		 */
		public byte getByte(int index) {
			return values[index];
		}

		/**
		 * Sets a byte.
		 * @param index The index.
		 * @param value The new byte value.
		 */
		public void setByte(int index, byte value) {
			values[index] = value;
		}
	}

	/**
	 * A WebAssembly array backed by shorts.
	 */
	public static final class OfShort extends DynamicWasmArray {
		private OfShort(DefType type, short[] values) {
			this.type = type;
			this.values = values;
		}

		private final DefType type;
		private final short[] values;

		@Override
		public HeapType heapType() {
			return type;
		}

		@Override
		public int length() {
			return values.length;
		}

		@Override
		public Object get(int index) {
			return values[index];
		}

		@Override
		public void set(int index, @Nullable Object value) {
			Objects.requireNonNull(value);
			values[index] = (short)value;
		}

		/**
		 * Gets a short.
		 * @param index The index.
		 * @return The short at the index.
		 */
		public short getShort(int index) {
			return values[index];
		}

		/**
		 * Sets a short.
		 * @param index The index.
		 * @param value The new short value.
		 */
		public void setShort(int index, short value) {
			values[index] = value;
		}
	}

	/**
	 * A WebAssembly array backed by ints.
	 */
	public static final class OfInt extends DynamicWasmArray {
		private OfInt(DefType type, int[] values) {
			this.type = type;
			this.values = values;
		}

		private final DefType type;
		private final int[] values;

		@Override
		public HeapType heapType() {
			return type;
		}

		@Override
		public int length() {
			return values.length;
		}

		@Override
		public Object get(int index) {
			return values[index];
		}

		@Override
		public void set(int index, @Nullable Object value) {
			Objects.requireNonNull(value);
			values[index] = (int)value;
		}

		/**
		 * Gets an int.
		 * @param index The index.
		 * @return The int at the index.
		 */
		public int getInt(int index) {
			return values[index];
		}

		/**
		 * Sets an int.
		 * @param index The index.
		 * @param value The new int value.
		 */
		public void setInt(int index, int value) {
			values[index] = value;
		}
	}

	/**
	 * A WebAssembly array backed by longs.
	 */
	public static final class OfLong extends DynamicWasmArray {
		OfLong(DefType type, long[] values) {
			this.type = type;
			this.values = values;
		}

		private final DefType type;
		private final long[] values;

		@Override
		public HeapType heapType() {
			return type;
		}

		@Override
		public int length() {
			return values.length;
		}

		@Override
		public Object get(int index) {
			return values[index];
		}

		@Override
		public void set(int index, @Nullable Object value) {
			Objects.requireNonNull(value);
			values[index] = (long)value;
		}

		/**
		 * Gets a long.
		 * @param index The index.
		 * @return The long at the index.
		 */
		public long getLong(int index) {
			return values[index];
		}

		/**
		 * Sets a long.
		 * @param index The index.
		 * @param value The new long value.
		 */
		public void setLong(int index, long value) {
			values[index] = value;
		}
	}

	/**
	 * A WebAssembly array backed by floats.
	 */
	public static final class OfFloat extends DynamicWasmArray {
		OfFloat(DefType type, float[] values) {
			this.type = type;
			this.values = values;
		}

		private final DefType type;
		private final float[] values;

		@Override
		public HeapType heapType() {
			return type;
		}

		@Override
		public int length() {
			return values.length;
		}

		@Override
		public Object get(int index) {
			return values[index];
		}

		@Override
		public void set(int index, @Nullable Object value) {
			Objects.requireNonNull(value);
			values[index] = (float)value;
		}

		/**
		 * Gets a float.
		 * @param index The index.
		 * @return The float at the index.
		 */
		public float getFloat(int index) {
			return values[index];
		}

		/**
		 * Sets a float.
		 * @param index The index.
		 * @param value The new float value.
		 */
		public void setFloat(int index, float value) {
			values[index] = value;
		}
	}

	/**
	 * A WebAssembly array backed by doubles.
	 */
	public static final class OfDouble extends DynamicWasmArray {
		OfDouble(DefType type, double[] values) {
			this.type = type;
			this.values = values;
		}

		private final DefType type;
		private final double[] values;

		@Override
		public HeapType heapType() {
			return type;
		}

		@Override
		public int length() {
			return values.length;
		}

		@Override
		public Object get(int index) {
			return values[index];
		}

		@Override
		public void set(int index, @Nullable Object value) {
			Objects.requireNonNull(value);
			values[index] = (double)value;
		}

		/**
		 * Gets a double.
		 * @param index The index.
		 * @return The double at the index.
		 */
		public double getDouble(int index) {
			return values[index];
		}

		/**
		 * Sets a double.
		 * @param index The index.
		 * @param value The new double value.
		 */
		public void setDouble(int index, double value) {
			values[index] = value;
		}
	}

}
