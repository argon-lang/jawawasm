package dev.argon.jawawasm.engine.tests.compiler;

import com.google.common.collect.ImmutableList;
import dev.argon.jawawasm.engine.aot.AOTEngine;
import dev.argon.jawawasm.engine.reflection.ReflectionEngine;
import dev.argon.jawawasm.format.ModuleFormatException;
import dev.argon.jawawasm.runtime.*;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.lang.constant.ClassDesc;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class RealizationConflictTest extends CompilerTestBase {
	private static final RuntimeContext CONTEXT = RuntimeOptions::new;

	private record Example(String name, String wat, String diagnostic) {}

	@TestFactory
	public Stream<DynamicTest> conflictingRealizations() {
		return Stream.of(new Example("result", """
			(module
			  (func (export "pair") (result i32 i64) i32.const 1 i64.const 2))
			""", "Conflicting types for result type"), new Example("result-reference-erasure", """
			(module
			  (func (export "pair") (result (ref null $heap) (ref null $heap))
			    ref.null $heap ref.null $heap))
			""", "Conflicting types for result type"), new Example("struct", """
			(module
			  (type $s (struct (field i32) (field i64)))
			  (global (export "value") (ref null $s) (ref.null $s)))
			""", "Conflicting JVM representations"), new Example("recursive-struct", """
			(module
			  (rec
			    (type $s (struct (field (ref null $s)) (field (mut i32)))))
			  (global (export "value") (ref null $s) (ref.null $s)))
			""", "Conflicting JVM representations"), new Example("array", """
			(module
			  (type $a (array (mut i32)))
			  (global (export "value") (ref null $a) (ref.null $a)))
			""", "Conflicting JVM representations"), new Example("function", """
			(module
			  (type $f (func (param i32) (result i64)))
			  (global (export "value") (ref null $f) (ref.null $f)))
			""", "Conflicting JVM representations"))
			.map(example -> DynamicTest.dynamicTest(example.name(), () -> checkConflict(example)));
	}

	private void checkConflict(Example example) throws Exception {
		var wat = temporaryDirectory.resolve(example.name() + ".wat");
		var runtime = Path.of(
			Objects.requireNonNull(
				Objects.requireNonNull(ResultVoid.class.getProtectionDomain().getCodeSource()).getLocation()
			).toURI()
		);
		var outputs = List
			.of(temporaryDirectory.resolve(example.name() + "-a"), temporaryDirectory.resolve(example.name() + "-b"));
		var classes = List.of(ClassDesc.of("conflict.a.Producer"), ClassDesc.of("conflict.b.Producer"));
		for(int i = 0; i < outputs.size(); i++) {
			// Different reference kinds still share the same erased result shape.
			Files.writeString(wat, example.wat().replace("$heap", i == 0 ? "extern" : "any"));
			var module = readWatFile(wat);
			Files.createDirectories(outputs.get(i));
			// Each producer gets a fresh compiler and no knowledge of the other producer.
			var producer = new AOTEngine(classes.get(i).packageName(), ImmutableList.of(runtime), outputs.get(i));
			producer.emitModule(module, "Producer", name -> {
				throw new AssertionError(name);
			});
		}
		try(var loader = new URLClassLoader(
			new java.net.URL[]{outputs.get(0).toUri().toURL(), outputs.get(1).toUri().toURL()},
			getClass().getClassLoader()
		)) {
			var a = loader.loadClass("conflict.a.Producer");
			var b = loader.loadClass("conflict.b.Producer");
			if(example.name().startsWith("result")) {
				assertNotEquals(a.getMethod("pair").getReturnType(), b.getMethod("pair").getReturnType());
			}
			else {
				assertNotEquals(
					a.getMethod("value").getGenericReturnType(),
					b.getMethod("value").getGenericReturnType()
				);
			}
			var instances = List.of(
				(WasmModule)a.getConstructor(RuntimeContext.class).newInstance(CONTEXT),
				(WasmModule)b.getConstructor(RuntimeContext.class).newInstance(CONTEXT)
			);
			for(int first : List.of(0, 1)) {
				var aot = new AOTEngine(
					"conflict.consumer",
					ImmutableList.of(outputs.get(0), outputs.get(1), runtime),
					temporaryDirectory.resolve("unused")
				);
				assertDoesNotThrow(() -> aot.addHostModule(classes.get(first)));
				assertDoesNotThrow(
					() -> aot.addHostModule(classes.get(first)),
					"Reusing the same realization is valid"
				);
				var aotError = assertThrows(
					ModuleFormatException.class,
					() -> aot.addHostModule(classes.get(1 - first))
				);
				assertTrue(
					Objects.requireNonNull(aotError.getMessage()).contains(example.diagnostic()),
					aotError::getMessage
				);
				assertDoesNotThrow(
					() -> aot.addHostModule(classes.get(first)),
					"A rejected realization must not replace the accepted one"
				);

				var reflection = new ReflectionEngine("conflict.reflection", CONTEXT);
				assertDoesNotThrow(() -> reflection.addHostModule(instances.get(first)));
				assertDoesNotThrow(
					() -> reflection.addHostModule(instances.get(first)),
					"Reusing the same realization is valid"
				);
				var reflectionError = assertThrows(
					ModuleFormatException.class,
					() -> reflection.addHostModule(instances.get(1 - first))
				);
				assertTrue(
					Objects.requireNonNull(reflectionError.getMessage()).contains(example.diagnostic()),
					reflectionError::getMessage
				);
				assertDoesNotThrow(
					() -> reflection.addHostModule(instances.get(first)),
					"A rejected realization must not replace the accepted one"
				);
			}
		}
	}
}
