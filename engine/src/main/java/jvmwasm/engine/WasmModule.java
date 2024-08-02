package jvmwasm.engine;

public interface WasmModule {
	WasmExport getExport(String name) throws Throwable;
}
