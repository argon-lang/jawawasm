package dev.argon.jawawasm.runtime;

import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * A WebAssembly table.
 * @param <T> The element type.
 */
public final class WasmTable<T extends @Nullable Object> {
	private WasmTable(long minSize, @Nullable Long maxSize, T initialValue) {
		if(minSize >= Integer.MAX_VALUE) {
			throw new IllegalArgumentException("WasmTable is too large for this engine");
		}

		items = new Object[(int)minSize];
		Arrays.fill(items, initialValue);

		this.maxSize = maxSize;
	}

	private final @Nullable Long maxSize;
	private @Nullable Object[] items;

	/**
	 * Creates a WebAssembly table.
	 * @param minSize The minimum table size.
	 * @param maxSize The maximum table size, or null if unlimited.
	 * @param initialValue The initial value of elements in the table.
	 * @return The table.
	 * @param <T> The element type.
	 */
	public static <T extends @Nullable Object> WasmTable<T> create(long minSize, @Nullable Long maxSize, T initialValue) {
		return new WasmTable<>(minSize, maxSize, initialValue);
	}

	/**
	 * Gets the size of the table.
	 * @return The current size of the table.
	 */
	public int size() {
		return items.length;
	}

	/**
	 * Gets the maximum size of the table.
	 * @return The maximum size or null if unlimited.
	 */
	public @Nullable Long maxSize() {
		return maxSize;
	}

	/**
	 * Gets an element of the table.
	 * @param index The index of the element to get.
	 * @return The element at the index.
	 */
	@SuppressWarnings("NullAway")
	public T get(int index) {
		@SuppressWarnings("unchecked")
		T item = (T)items[index];
		return item;
	}

	/**
	 * Sets an element of the table.
	 * @param index The index of the element to set.
	 * @param value The new value of the element.
	 */
	public void set(int index, T value) {
		items[index] = value;
	}

	/**
	 * Grow the table.
	 * @param fillValue The value to fill in for the new elements.
	 * @param growBy The number of elements to add to the table.
	 * @return The old size of the table or -1 if the table could not be resized.
	 */
	public int grow(T fillValue, int growBy) {
		if(
			growBy < 0 ||
				(long)growBy + items.length > Integer.MAX_VALUE ||
				(maxSize != null && (long)growBy + items.length > maxSize)
		) {
			return -1;
		}

		if(growBy == 0) {
			return items.length;
		}

		int newSize = growBy + items.length;

		Object[] newItems;
		try {
			newItems = new Object[newSize];
		}
		catch(OutOfMemoryError _) {
			return -1;
		}

		int oldLength = items.length;

		System.arraycopy(items, 0, newItems, 0, items.length);
		Arrays.fill(newItems, oldLength, newItems.length, fillValue);
		items = newItems;
		return oldLength;
	}

	/**
	 * Fill a range of the table with a value.
	 * @param i The start of the range.
	 * @param n The number of items in the range.
	 * @param value The fill value.
	 */
	public void fill(int i, int n, T value) {
		Objects.checkFromIndexSize(i, n, items.length);
		Arrays.fill(items, i, i + n, value);
	}

	/**
	 * Ensures that the table has a minimum size.
	 * @param min The minimum number of elements.
	 */
	public void ensureMinimumSize(int min) {
		if(items.length < min) {
			throw new ModuleLinkException("incompatible import type: Table size is too small");
		}
	}

	/**
	 * Copy values from an array.
	 * @param d The starting table index.
	 * @param s The starting array index.
	 * @param n The number of items to copy.
	 * @param values The source values.
	 */
	public void copyFromArray(int d, int s, int n, T[] values) {
		System.arraycopy(values, s, items, d, n);
	}

	/**
	 * Copy values from an array.
	 * @param d The starting table index.
	 * @param s The starting array index.
	 * @param n The number of items to copy.
	 * @param values The source values.
	 */
	public void copyFrom(int d, int s, int n, WasmTable<T> values) {
		System.arraycopy(values.items, s, items, d, n);
	}

	/**
	 * Implements table.get for tables with 32 bit indexes
	 * @param index The index of the element to get.
	 * @param table The table containing the element.
	 * @return The value at the index.
	 * @param <T> The element type.
	 */
	public static <T extends @Nullable Object> T table_get(int index, WasmTable<T> table) {
		return table.get(index);
	}

	/**
	 * Implements table.get for tables with 64 bit indexes
	 * @param index The index of the element to get.
	 * @param table The table containing the element.
	 * @return The value at the index.
	 * @param <T> The element type.
	 */
	public static <T extends @Nullable Object> T table_get(long index, WasmTable<T> table) {
		if(index < 0 || index > Integer.MAX_VALUE) {
			throw new IndexOutOfBoundsException();
		}

		return table.get((int)index);
	}

	/**
	 * Implements table.set for tables with 32 bit indexes
	 * @param index The index of the element to get.
	 * @param value The new value of the element.
	 * @param table The table containing the element.
	 * @param <T> The element type.
	 */
	public static <T extends @Nullable Object> void table_set(int index, T value, WasmTable<T> table) {
		table.set(index, value);
	}

	/**
	 * Implements table.set for tables with 64 bit indexes
	 * @param index The index of the element to get.
	 * @param value The new value of the element.
	 * @param table The table containing the element.
	 * @param <T> The element type.
	 */
	public static <T extends @Nullable Object> void table_set(long index, T value, WasmTable<T> table) {
		if(index < 0 || index > Integer.MAX_VALUE) {
			throw new IndexOutOfBoundsException();
		}

		table.set((int)index, value);
	}

	/**
	 * Grow the table.
	 * @param fillValue The value to fill in for the new elements.
	 * @param growBy The number of elements to add to the table.
	 * @param table The table to grow.
	 * @return The old size of the table or -1 if the table could not be resized.
	 * @param <T> The element type.
	 */
	public static <T extends @Nullable Object> int table_grow(T fillValue, int growBy, WasmTable<T> table) {
		return table.grow(fillValue, growBy);
	}

	/**
	 * Grow the table.
	 * @param fillValue The value to fill in for the new elements.
	 * @param growBy The number of elements to add to the table.
	 * @param table The table to grow.
	 * @return The old size of the table or -1 if the table could not be resized.
	 * @param <T> The element type.
	 */
	public static <T extends @Nullable Object> long table_grow(T fillValue, long growBy, WasmTable<T> table) {
		if(growBy > Integer.MAX_VALUE || growBy < 0) {
			return -1;
		}

		return table.grow(fillValue, (int)growBy);
	}

	/**
	 * Fill a range of the table with a value.
	 * @param i The start of the range.
	 * @param fillValue The fill value.
	 * @param n The number of items in the range.
	 * @param table The table to fill.
	 * @param <T> The element type.
	 */
	public static <T extends @Nullable Object> void table_fill(int i, T fillValue, int n, WasmTable<T> table) {
		table.fill(i, n, fillValue);
	}

	/**
	 * Fill a range of the table with a value.
	 * @param i The start of the range.
	 * @param fillValue The fill value.
	 * @param n The number of items in the range.
	 * @param table The table to fill.
	 * @param <T> The element type.
	 */
	public static <T extends @Nullable Object> void table_fill(long i, T fillValue, long n, WasmTable<T> table) {
		if(i > Integer.MAX_VALUE || i < 0 || n > Integer.MAX_VALUE || n < 0) {
			throw new IndexOutOfBoundsException();
		}

		table.fill((int)i, (int)n, fillValue);
	}

	/**
	 * Copy values from an array.
	 * @param d The starting table index.
	 * @param s The starting array index.
	 * @param n The number of items to copy.
	 * @param values The source values.
	 * @param table The destination table.
	 * @param <T> The element type.
	 */
	public static <T extends @Nullable Object> void copyFromArray(int d, int s, int n, T[] values, WasmTable<T> table) {
		table.copyFromArray(d, s, n, values);
	}

	/**
	 * Copy values from an array.
	 * @param d The starting table index.
	 * @param s The starting array index.
	 * @param n The number of items to copy.
	 * @param table The destination table.
	 * @param values The source values.
	 * @param <T> The element type.
	 */
	public static <T extends @Nullable Object> void copyFromArray(long d, int s, int n, T[] values, WasmTable<T> table) {
		if(d < 0 || d > Integer.MAX_VALUE) {
			throw new IndexOutOfBoundsException();
		}

		table.copyFromArray((int)d, s, n, values);
	}

	/**
	 * Copy values between tables.
	 * @param d The starting table index.
	 * @param s The starting array index.
	 * @param n The number of items to copy.
	 * @param destTable The destination table.
	 * @param srcTable The source table.
	 * @param <T> The element type.
	 */
	public static <T extends @Nullable Object> void copy(int d, int s, int n, WasmTable<T> destTable, WasmTable<T> srcTable) {
		destTable.copyFrom(d, s, n, srcTable);
	}

	/**
	 * Copy values between tables.
	 * @param d The starting table index.
	 * @param s The starting array index.
	 * @param n The number of items to copy.
	 * @param destTable The destination table.
	 * @param srcTable The source table.
	 * @param <T> The element type.
	 */
	public static <T extends @Nullable Object> void copy(long d, long s, long n, WasmTable<T> destTable, WasmTable<T> srcTable) {
		if(d < 0 || d > Integer.MAX_VALUE || s < 0 || s > Integer.MAX_VALUE || n < 0 || n > Integer.MAX_VALUE) {
			throw new IndexOutOfBoundsException();
		}

		destTable.copyFrom((int)d, (int)s, (int)n, srcTable);
	}

}
