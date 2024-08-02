package jvmwasm.engine;

public class WasmElements {
	public WasmElements(Object[] values) {
		this.values = values;
	}

	private final Object[] values;

	public int size() {
		return values.length;
	}

	public Object get(int i) {
		return values[i];
	}
}
