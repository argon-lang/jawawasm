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
	public long size() {
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
	 * Gets an element of the table.
	 * @param index The index of the element to get.
	 * @return The element at the index.
	 */
	@SuppressWarnings("NullAway")
	public T get(long index) {
		Objects.checkIndex(index, items.length);
		return get((int)index);
	}

	/**
	 * Sets an element of the table.
	 * @param index The index of the element to set.
	 * @param value The new value of the element.
	 */
	public void set(long index, T value) {
		set((int)index, value);
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
	 * @param growBy The number of elements to add to the table.
	 * @return The new size of the table or -1 if the table could not be resized.
	 */
	public int grow(int growBy) {
		if(growBy < 0 || (long)growBy + items.length > Integer.MAX_VALUE) {
			return -1;
		}

		int newSize = growBy + items.length;

		Object[] newItems;
		try {
			newItems = new Object[newSize];
		}
		catch(OutOfMemoryError _) {
			return -1;
		}

		System.arraycopy(items, 0, newItems, 0, items.length);
		items = newItems;
		return newItems.length;
	}

	/**
	 * Grow the table.
	 * @param growBy The number of elements to add to the table.
	 * @return The new size of the table or -1 if the table could not be resized.
	 */
	public long grow(long growBy) {
		if(growBy > Integer.MAX_VALUE || growBy < 0) {
			return -1;
		}

		return grow((int)growBy);
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
		return table.get(index);
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
		table.set(index, value);
	}

}
