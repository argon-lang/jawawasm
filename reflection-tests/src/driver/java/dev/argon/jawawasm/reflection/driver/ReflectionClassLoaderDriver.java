package dev.argon.jawawasm.reflection.driver;

import dev.argon.jawawasm.engine.reflection.ReflectionEngine;
import dev.argon.jawawasm.engine.reflection.ReflectionExport;
import dev.argon.jawawasm.format.binary.ModuleReader;
import dev.argon.jawawasm.runtime.RuntimeOptions;
import dev.argon.jawawasm.runtime.ResultI32;
import dev.argon.jawawasm.runtime.UnreachableTrap;

import java.lang.reflect.InvocationTargetException;
import static java.util.Objects.requireNonNull;

public final class ReflectionClassLoaderDriver {
	private ReflectionClassLoaderDriver() {}

	public static boolean run() {
		try(var wasm = requireNonNull(ReflectionClassLoaderDriver.class.getResourceAsStream("reflection.wasm"))) {
			var options = RuntimeOptions.builder().build();
			var engine = new ReflectionEngine("test.generated", () -> options);
			var module = engine.instantiateModule(new ModuleReader(wasm).readModule(), name -> {
				throw new AssertionError("Unexpected import: " + name);
			});
			var answer = (ReflectionExport.FunctionExport)requireNonNull(module.exports().get("answer"));
			if(ResultI32.get((ResultI32)requireNonNull(answer.method().invoke(module.module()))).item0 != 42) {
				return false;
			}
			var trap = (ReflectionExport.FunctionExport)requireNonNull(module.exports().get("trap"));
			try {
				ResultI32.get((ResultI32)requireNonNull(trap.method().invoke(module.module())));
			}
			catch(InvocationTargetException e) {
				if((e.getCause() instanceof UnreachableTrap)) {
					return true;
				}
				throw e;
			}
			catch(UnreachableTrap expected) {
				// Trampolined execution can throw directly from ResultI32.get.
				return true;
			}

			return false;
		}
		catch(Throwable e) {
			e.printStackTrace();
			return false;
		}
	}
}
