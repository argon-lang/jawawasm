package dev.argon.jawawasm.app;

import dev.argon.jawawasm.app.wast.WastLoader;
import dev.argon.jawawasm.engine.ModuleResolver;
import dev.argon.jawawasm.engine.reflection.ReflectionEngine;
import dev.argon.jawawasm.engine.compiler.NameMangling;
import dev.argon.jawawasm.engine.reflection.ReflectionExport;
import dev.argon.jawawasm.engine.reflection.ReflectionModule;
import dev.argon.jawawasm.format.ModuleFormatException;
import dev.argon.jawawasm.format.modules.Module;
import dev.argon.jawawasm.runtime.*;
import org.jspecify.annotations.Nullable;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutionException;


/**
 * An executor for WAST scripts that uses a class loader.
 */
public final class ScriptReflectionExecutor extends ScriptExecutor<ReflectionModule> {
	public ScriptReflectionExecutor(String packageName, WastLoader loader, PrintWriter output) {
		super(loader, output);

		RuntimeContext context = new RuntimeContext() {
			@Override
			public MemoryAllocator allocator() {
				return allocator;
			}
		};

		engine = new ReflectionEngine(packageName, context);
	}

	private final ReflectionEngine engine;

	@Override
	ReflectionModule getSpecTestModule(PrintWriter output) throws ModuleFormatException, ModuleLinkException {
		var specTest = new SpecTestModuleInstance(allocator, output);
		return engine.addHostModule(specTest);
	}

	@Override
	ReflectionModule instantiateModule(Module module, ModuleResolver<ReflectionModule> resolver) throws ExecutionException {
		return engine.instantiateModule(module, resolver);
	}

	@Override
	@Nullable Object[] invokeModuleExport(ReflectionModule module, String exportName, @Nullable Object[] args) throws ExecutionException {
		try {
			if(!(module.exports().get(exportName) instanceof ReflectionExport.FunctionExport export)) {
				throw new RuntimeException("Could not find function export");
			}

			Object result = export.method().invoke(module.module(), args);

			var stepClass = Arrays.stream(export.method().getReturnType().getDeclaredClasses())
				.filter(c -> c.getSimpleName().equals("Step"))
				.findAny()
				.orElseThrow();

			while(stepClass.isInstance(result)) {
				Method stepMethod = stepClass.getMethod("step");
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
		catch(IllegalAccessException | NoSuchMethodException e) {
			throw new RuntimeException(e);
		}
		catch(InvocationTargetException e) {
			throw new ExecutionException(e.getCause());
		}
	}

	@Override
	@Nullable Object getGlobalExport(ReflectionModule module, String exportName) {
		try {
			return switch(module.exports().get(exportName)) {
				case ReflectionExport.GlobalExportConst(var method) ->
					method.invoke(module.module());

				case ReflectionExport.GlobalExportVar(var method) -> {
					var globalObj = method.invoke(module.module());
					yield switch(globalObj) {
						case GlobalI32 g -> g.get();
						case GlobalI64 g -> g.get();
						case GlobalF32 g -> g.get();
						case GlobalF64 g -> g.get();
						case GlobalRef<?> g -> g.get();
						default -> throw new RuntimeException("Unexpected global container type");
					};
				}

				case null, default -> throw new RuntimeException("Could not find global export");
			};
		}
		catch(IllegalAccessException e) {
			throw new RuntimeException(e);
		}
		catch(InvocationTargetException e) {
			if(e.getCause() instanceof RuntimeException re) {
				throw re;
			}
			throw new RuntimeException(e.getCause());
		}
	}
}
