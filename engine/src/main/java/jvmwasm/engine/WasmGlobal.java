package jvmwasm.engine;

import jvmwasm.format.types.GlobalType;
import jvmwasm.format.types.Mut;

public final class WasmGlobal implements WasmExport {
	public WasmGlobal(GlobalType type, Object value) {
		this.type = type;
		this.value = value;
	}


	private final GlobalType type;
	private volatile Object value;

	public GlobalType type() {
		return type;
	}

	public Object get() {
		return value;
	}

	public void set(Object value) {
		if(type.mutability() == Mut.Const) {
			throw new IllegalStateException();
		}

		this.value = value;
	}
}
