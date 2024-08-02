package jvmwasm.engine;

import java.util.ArrayList;
import java.util.List;

import jvmwasm.format.types.Limits;
import jvmwasm.format.types.RefType;
import jvmwasm.format.types.TableType;
import org.jspecify.annotations.Nullable;

public final class WasmTable implements WasmExport {
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


	public synchronized TableType type() {
		return new TableType(new Limits(values.size(), maxSize), elementType);
	}


	public synchronized int size() {
		return values.size();
	}

	public synchronized Object get(int i) {
		return values.get(i);
	}

	public synchronized void set(int i, @Nullable Object value) {
		values.set(i, value);
	}

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
