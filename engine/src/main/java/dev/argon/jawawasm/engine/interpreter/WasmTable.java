package dev.argon.jawawasm.engine.interpreter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.argon.jawawasm.format.types.Limits;
import dev.argon.jawawasm.format.types.TableType;
import dev.argon.jawawasm.runtime.Util;
import org.jspecify.annotations.Nullable;

/**
 * A WebAssembly table.
 */
public final class WasmTable implements WasmExport {
	/**
	 * Create a table.
	 * @param type The table type.
	 * @param initialValue The initial value for elements of the table.
	 */
	public WasmTable(TableType type, @Nullable Object initialValue) {
		this.tableType = type;
		values = new ArrayList<>((int)type.limits().min());
		for(int i = 0; i < type.limits().min(); ++i) {
			values.add(initialValue);
		}
	}

	private final TableType tableType;
	private final List<@Nullable Object> values;

	/**
	 * Gets the table type.
	 * @return The table type.
	 */
	public synchronized TableType type() {
		return new TableType(
			tableType.addrType(),
			new Limits(values.size(), tableType.limits().max()),
			tableType.elementType()
		);
	}

	/**
	 * Gets the size of the table.
	 * @return The size of the table.
	 */
	public synchronized long size() {
		return values.size();
	}

	/**
	 * Gets a table element.
	 * @param i The index.
	 * @return The element.
	 */
	public synchronized @Nullable Object get(long i) {
		Objects.checkIndex(i, values.size());
		return values.get((int)i);
	}

	/**
	 * Sets a table element.
	 * @param i The index.
	 * @param value The value.
	 */
	public synchronized void set(long i, @Nullable Object value) {
		Objects.checkIndex(i, values.size());
		values.set((int)i, value);
	}

	/**
	 * Grow the table.
	 * @param n The number of new elements.
	 * @param value The value to set for new elements.
	 * @return The old size.
	 */
	public synchronized long grow(long n, @Nullable Object value) {
		int oldSize = values.size();

		if(n < 0 || (tableType.limits().max() != null && oldSize + n > tableType.limits().max()) || oldSize + n < 0 || n > Integer.MAX_VALUE) {
			return -1;
		}

		for(int i = 0; i < n; ++i) {
			values.add(value);
		}

		return oldSize;
	}

	/**
	 * Fill the table.
	 * @param n The number of values to fill.
	 * @param val The fill value.
	 * @param i The start index.
	 * @param table The table to fill.
	 */
	public static void fill(long n, @Nullable Object val, long i, WasmTable table) {
		if(!Util.sumInRange(i, n, table.size())) {
			throw new IndexOutOfBoundsException();
		}

		while(n != 0) {
			table.set(i, val);
			++i;
			--n;
		}
	}

	/**
	 * Copy values between tables.
	 * @param n The number of values to copy.
	 * @param s The start index for tableY.
	 * @param d The start index for tableX.
	 * @param tableX The destination table.
	 * @param tableY The source table.
	 */
	public static void copy(long n, long s, long d, WasmTable tableX, WasmTable tableY) {
		if(!Util.sumInRange(s, n, tableY.size()) || !Util.sumInRange(d, n, tableX.size())) {
			throw new IndexOutOfBoundsException();
		}

		if(d <= s) {
			while(n != 0) {
				Object value = tableY.get(s);
				tableX.set(d, value);

				++d;
				++s;
				--n;
			}
		}
		else {
			while(n != 0) {
				Object value = tableY.get(s + n - 1);
				tableX.set(d + n - 1, value);
				--n;
			}
		}
	}

	/**
	 * Initialize a table.
	 * @param d The start index for table.
	 * @param s The start index for elem.
	 * @param n The number of values to copy.
	 * @param table The destination table.
	 * @param elem The source elements.
	 */
	public static void init(long d, int s, int n, WasmTable table, WasmElements elem) {
		if(!Util.sumInRange(s, n, elem.size()) || !Util.sumInRange(d, n, table.size())) {
			throw new IndexOutOfBoundsException();
		}

		while(n != 0) {
			var val = elem.get(s);
			table.set(d, val);

			++d;
			++s;
			--n;
		}
	}

	@Override
	public String toString() {
		return values.toString();
	}
}
