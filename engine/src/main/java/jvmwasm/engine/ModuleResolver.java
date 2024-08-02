package jvmwasm.engine;

public interface ModuleResolver {
	WasmModule resolve(String name) throws Throwable;
}
