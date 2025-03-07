package dev.argon.jawawasm.engine;

import dev.argon.jawawasm.format.types.*;

public sealed abstract class WasmArray implements WasmEq {

	public abstract int length();
	public abstract Object get(int index);
	public abstract void set(int index, Object value);

	static WasmArray create(DefType type, int length) {
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

	public static final class OfObject extends WasmArray {
		private OfObject(DefType type, Object[] values) {
			this.type = type;
			this.values = values;
		}

		private final DefType type;
		private final Object[] values;

		@Override
		public HeapType heapType() {
			return type;
		}

		public int length() {
			return values.length;
		}

		public Object get(int index) {
			return values[index];
		}

		public void set(int index, Object value) {
			values[index] = value;
		}
	}

	public static final class OfByte extends WasmArray {
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

		public int length() {
			return values.length;
		}

		public Object get(int index) {
			return values[index];
		}

		public void set(int index, Object value) {
			values[index] = (byte)value;
		}

		public byte getByte(int index) {
			return values[index];
		}

		public void setByte(int index, byte value) {
			values[index] = value;
		}
	}

	public static final class OfShort extends WasmArray {
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

		public int length() {
			return values.length;
		}

		public Object get(int index) {
			return values[index];
		}

		public void set(int index, Object value) {
			values[index] = (short)value;
		}

		public short getShort(int index) {
			return values[index];
		}

		public void setShort(int index, short value) {
			values[index] = value;
		}
	}

	public static final class OfInt extends WasmArray {
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

		public int length() {
			return values.length;
		}

		public Object get(int index) {
			return values[index];
		}

		public void set(int index, Object value) {
			values[index] = (short)value;
		}

		public int getInt(int index) {
			return values[index];
		}

		public void setInt(int index, int value) {
			values[index] = value;
		}
	}

	public static final class OfLong extends WasmArray {
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

		public int length() {
			return values.length;
		}

		public Object get(int index) {
			return values[index];
		}

		public void set(int index, Object value) {
			values[index] = (long) value;
		}

		public long getLong(int index) {
			return values[index];
		}

		public void setLong(int index, long value) {
			values[index] = value;
		}
	}

	public static final class OfFloat extends WasmArray {
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

		public int length() {
			return values.length;
		}

		public Object get(int index) {
			return values[index];
		}

		public void set(int index, Object value) {
			values[index] = (float) value;
		}

		public float getFloat(int index) {
			return values[index];
		}

		public void setFloat(int index, float value) {
			values[index] = value;
		}
	}

	public static final class OfDouble extends WasmArray {
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

		public int length() {
			return values.length;
		}

		public Object get(int index) {
			return values[index];
		}

		public void set(int index, Object value) {
			values[index] = (double) value;
		}

		public double getDouble(int index) {
			return values[index];
		}

		public void setDouble(int index, double value) {
			values[index] = value;
		}
	}

}
