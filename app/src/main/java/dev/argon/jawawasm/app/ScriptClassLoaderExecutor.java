package dev.argon.jawawasm.app;

import dev.argon.jawawasm.engine.ModuleResolver;
import dev.argon.jawawasm.engine.classloader.ClassLoaderEngine;
import dev.argon.jawawasm.engine.compiler.NameMangling;
import dev.argon.jawawasm.format.modules.Module;
import dev.argon.jawawasm.runtime.ModuleLinkException;
import dev.argon.jawawasm.runtime.WasmModule;
import org.jspecify.annotations.Nullable;

import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.ExecutionException;


/**
 * An executor for WAST scripts that uses a class loader.
 */
public final class ScriptClassLoaderExecutor extends ScriptExecutor<WasmModule> {
	/**
	 * Create a ScriptInterpreter.
	 * @param wasmExecutable Path to the reference interpreter.
	 * @param output Writer to receive output.
	 */
	public ScriptClassLoaderExecutor(String packageName, Path wasmExecutable, PrintWriter output) {
		super(wasmExecutable, output);

		engine = new ClassLoaderEngine(packageName, allocator);
	}

	private final ClassLoaderEngine engine;

	@Override
	WasmModule getSpecTestModule(PrintWriter output) {
		throw new RuntimeException("Not implemented");
	}

	@Override
	WasmModule instantiateModule(Module module, ModuleResolver<WasmModule> resolver) throws ModuleLinkException, ExecutionException {
		return engine.instantiateModule(module, resolver);
	}

	@Override
	@Nullable Object[] invokeModuleExport(WasmModule module, String exportName, @Nullable Object[] args) throws ExecutionException {
		try {
			String escapedExportName = NameMangling.escapeName(exportName);

			Method method = Arrays.stream(module.getClass().getMethods())
				.filter(m -> m.canAccess(module) && m.getName().equals(escapedExportName))
				.findFirst()
				.orElseThrow();

			Object result = method.invoke(module, args);

			for(;;) {
				Method stepMethod;
				try {
					stepMethod = result.getClass().getMethod("step");
				}
				catch(NoSuchMethodException e) {
					break;
				}

				result = stepMethod.invoke(result);
			}

			Object endResult = result;

			var fields = Arrays.stream(endResult.getClass().getDeclaredFields())
				.filter(f -> f.canAccess(endResult) && f.getName().startsWith("item"))
				.sorted(Comparator.comparing(f -> Integer.parseInt(f.getName().substring(4))))
				.toList();

			Object[] results = new Object[fields.size()];
			for(int i = 0; i < fields.size(); ++i) {
				results[i] = fields.get(i).get(endResult);
			}
			return results;
		}
		catch(IllegalAccessException e) {
			throw new RuntimeException(e);
		}
		catch(InvocationTargetException e) {
			throw new ExecutionException(e.getCause());
		}
	}

	@Override
	@Nullable Object getGlobalExport(WasmModule module, String exportName) {
		throw new RuntimeException("Not implemented");
	}
}
