package dev.argon.jawawasm.runtime;

import java.util.Objects;

public sealed abstract class WasmArray implements WasmEq permits WasmArrayMutable, WasmArrayImmutable {
	/**
	 * Gets the length of the array.
	 * @return The length.
	 */
	public abstract int length();

	/**
	 * Gets the underlying array.
	 * @return The array.
	 */
	protected abstract Object unsafeGetArray();


	public static void copy(WasmArrayMutable dest, int d, WasmArray src, int s, int n) {
		Objects.requireNonNull(dest);
		Objects.requireNonNull(src);
		Objects.checkFromIndexSize(s, n, src.length());
		Objects.checkFromIndexSize(d, n, dest.length());

		if(n == 0) {
			return;
		}

		System.arraycopy(src.unsafeGetArray(), s, dest.unsafeGetArray(), d, n);
	}

	public static void copyFromArray(WasmArrayMutable dest, int d, int s, int n, Object[] src) {
		Objects.requireNonNull(dest);
		Objects.requireNonNull(src);
		Objects.checkFromIndexSize(s, n, src.length);
		Objects.checkFromIndexSize(d, n, dest.length());

		if(n == 0) {
			return;
		}

		System.arraycopy(src, s, dest.unsafeGetArray(), d, n);
	}


	protected static void initArrayFromData(byte[] array, int d, byte[] data, int s, int n) {
		Objects.checkFromIndexSize(d, n, array.length);
		Objects.checkFromIndexSize(s, n, data.length);

		if(n == 0) {
			return;
		}

		System.arraycopy(data, s, array, d, n);
	}

	protected static void initArrayFromData(short[] array, int d, byte[] data, int s, int n) {
		Objects.checkFromIndexSize(d, n, array.length);
		if(n * 2 < 0) throw new IndexOutOfBoundsException("Number of items copied results in overflow when multiplied by byte size.");
		Objects.checkFromIndexSize(s, n * 2, data.length);
		for(int i = 0; i < n; ++i) {
			array[d + i] = (short)((data[s + i * 2] & 0xFF) | ((data[s + i * 2 + 1] & 0xFF) << 8));
		}
	}

	protected static void initArrayFromData(int[] array, int d, byte[] data, int s, int n) {
		Objects.checkFromIndexSize(d, n, array.length);
		if(n * 4 < 0) throw new IndexOutOfBoundsException("Number of items copied results in overflow when multiplied by byte size.");
		Objects.checkFromIndexSize(s, n * 4, data.length);
		for(int i = 0; i < n; ++i) {
			array[d + i] = (data[s + i * 4] & 0xFF) |
				((data[s + i * 4 + 1] & 0xFF) << 8) |
				((data[s + i * 4 + 2] & 0xFF) << 16) |
				((data[s + i * 4 + 3] & 0xFF) << 24);
		}
	}

	protected static void initArrayFromData(float[] array, int d, byte[] data, int s, int n) {
		Objects.checkFromIndexSize(d, n, array.length);
		if(n * 4 < 0) throw new IndexOutOfBoundsException("Number of items copied results in overflow when multiplied by byte size.");
		Objects.checkFromIndexSize(s, n * 4, data.length / 4);
		for(int i = 0; i < n; ++i) {
			array[d + i] = Float.intBitsToFloat(
				(data[s + i * 4] & 0xFF) |
					((data[s + i * 4 + 1] & 0xFF) << 8) |
					((data[s + i * 4 + 2] & 0xFF) << 16) |
					((data[s + i * 4 + 3] & 0xFF) << 24)
			);
		}
	}

	protected static void initArrayFromData(long[] array, int d, byte[] data, int s, int n) {
		Objects.checkFromIndexSize(d, n, array.length);
		if(n * 8 < 0) throw new IndexOutOfBoundsException("Number of items copied results in overflow when multiplied by byte size.");
		Objects.checkFromIndexSize(s, n * 8, data.length);
		for(int i = 0; i < n; ++i) {
			array[d + i] = (long)(data[s + i * 8] & 0xFF) |
				((long)(data[s + i * 8 + 1] & 0xFF) << 8) |
				((long)(data[s + i * 8 + 2] & 0xFF) << 16) |
				((long)(data[s + i * 8 + 3] & 0xFF) << 24) |
				((long)(data[s + i * 8 + 4] & 0xFF) << 32) |
				((long)(data[s + i * 8 + 5] & 0xFF) << 40) |
				((long)(data[s + i * 8 + 6] & 0xFF) << 48) |
				((long)(data[s + i * 8 + 7] & 0xFF) << 56);
		}
	}

	protected static void initArrayFromData(double[] array, int d, byte[] data, int s, int n) {
		Objects.checkFromIndexSize(d, n, array.length);
		if(n * 8 < 0) throw new IndexOutOfBoundsException("Number of items copied results in overflow when multiplied by byte size.");
		Objects.checkFromIndexSize(s, n * 8, data.length / 8);
		for(int i = 0; i < n; ++i) {
			array[d + i] = Double.longBitsToDouble(
				(long)(data[s + i * 8] & 0xFF) |
					((long)(data[s + i * 8 + 1] & 0xFF) << 8) |
					((long)(data[s + i * 8 + 2] & 0xFF) << 16) |
					((long)(data[s + i * 8 + 3] & 0xFF) << 24) |
					((long)(data[s + i * 8 + 4] & 0xFF) << 32) |
					((long)(data[s + i * 8 + 5] & 0xFF) << 40) |
					((long)(data[s + i * 8 + 6] & 0xFF) << 48) |
					((long)(data[s + i * 8 + 7] & 0xFF) << 56)
			);
		}
	}

	protected static void initArrayFromData(V128[] array, int d, byte[] data, int s, int n) {
		Objects.checkFromIndexSize(d, n, array.length);
		if(n * 16 < 0) throw new IndexOutOfBoundsException("Number of items copied results in overflow when multiplied by byte size.");
		Objects.checkFromIndexSize(s, n * 16, data.length / 16);
		for(int i = 0; i < n; ++i) {
			int i2 = i;
			array[d + i] = V128.build8(j -> data[s + i2 * 16 + j]);
		}
	}
}
