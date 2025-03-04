package dev.argon.jawawasm.engine;

public final class WebAssemblyException extends Exception {
	public WebAssemblyException(WasmTag tag, Object[] values) {
		super("Exception thrown by WebAssembly code");
		this.tag = tag;
		this.values = values;
	}

	private final WasmTag tag;
	private final Object[] values;

	public WasmTag getTag() {
		return tag;
	}

	public Object[] getValues() {
		return values;
	}
}
