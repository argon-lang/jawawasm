package dev.argon.jawawasm.engine;

import java.util.ArrayList;
import java.util.List;

import dev.argon.jawawasm.format.types.Limits;
import dev.argon.jawawasm.format.types.RefType;
import dev.argon.jawawasm.format.types.TableType;
import org.jspecify.annotations.Nullable;

/**
 * A WebAssembly table.
 */
public final class WasmTable implements WasmExport {
	/**
	 * Create a table.
	 * @param type The table type.
	 */
	public WasmTable(TableType type) {
		elementType = type.elementType();
		maxSize = type.limits().max();
		values = new ArrayList<>(type.limits().min());
		for(int i = 0; i < type.limits().min(); ++i) {
			values.add(null);
		}
	}

	private final RefType elementType;
	private final Integer maxSize;
	private final List<Object> values;

	/**
	 * Gets the table type.
	 * @return The table type.
	 */
	public synchronized TableType type() {
		return new TableType(new Limits(values.size(), maxSize), elementType);
	}

	/**
	 * Gets the size of the table.
	 * @return The size of the table.
	 */
	public synchronized int size() {
		return values.size();
	}

	/**
	 * Gets a table element.
	 * @param i The index.
	 * @return The element.
	 */
	public synchronized Object get(int i) {
		return values.get(i);
	}

	/**
	 * Sets a table element.
	 * @param i The index.
	 * @param value The value.
	 */
	public synchronized void set(int i, @Nullable Object value) {
		values.set(i, value);
	}

	/**
	 * Grow the table.
	 * @param n The number of new elements.
	 * @param value The value to set for new elements.
	 * @return The old size.
	 */
	public synchronized int grow(int n, Object value) {
		int oldSize = values.size();

		if(n < 0 || (maxSize != null && oldSize + n > maxSize) || oldSize + n < 0) {
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
	public static void fill(int n, Object val, int i, WasmTable table) {
		if(!Util.sumInRange(i, + n, table.size())) {
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
	public static void copy(int n, int s, int d, WasmTable tableX, WasmTable tableY) {
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
	public static void init(int d, int s, int n, WasmTable table, WasmElements elem) {
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
