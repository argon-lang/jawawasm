package dev.argon.jawawasm.app;

import dev.argon.jawawasm.engine.ModuleResolver;
import dev.argon.jawawasm.engine.interpreter.*;
import dev.argon.jawawasm.engine.interpreter.WasmModule;
import dev.argon.jawawasm.runtime.*;
import dev.argon.jawawasm.format.modules.Module;
import org.jspecify.annotations.Nullable;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;


/**
 * An interpreter for WAST scripts.
 */
public final class ScriptInterpreter extends ScriptExecutor<WasmModule> {
	/**
	 * Create a ScriptInterpreter.
	 * @param wasmExecutable Path to the reference interpreter.
	 * @param output Writer to receive output.
	 */
	public ScriptInterpreter(Path wasmExecutable, PrintWriter output) {
		super(wasmExecutable, output);

		engine = new Engine(allocator);
	}

	private final Engine engine;

	@Override
	WasmModule getSpecTestModule(PrintWriter output) {
		return new SpecTestModuleInterpreter(allocator, output);
	}

	@Override
	WasmModule instantiateModule(Module module, ModuleResolver<WasmModule> resolver) throws ModuleLinkException, ExecutionException {
		return engine.instantiateModule(module, resolver);
	}

	@Override
	@Nullable Object[] invokeModuleExport(WasmModule module, String exportName, @Nullable Object[] args) throws ExecutionException {
		var export = (DynamicWasmFunction)module.getExport(exportName);
		Objects.requireNonNull(export);
		return DynamicFunctionResult.resolveWith(() -> export.invoke(args));
	}

	@Override
	@Nullable Object getGlobalExport(WasmModule module, String exportName) {
		var export = (WasmGlobal)module.getExport(exportName);
		Objects.requireNonNull(export);
		return export.get();
	}
}
