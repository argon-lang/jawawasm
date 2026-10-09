package dev.argon.jawawasm.engine.tests.compiler;

import dev.argon.jawawasm.engine.compiler.CompilerOptions;
import dev.argon.jawawasm.engine.compiler.ModuleCompiler;
import dev.argon.jawawasm.engine.compiler.WasmClassGenerator;
import dev.argon.jawawasm.engine.validator.ModuleValidator;
import dev.argon.jawawasm.format.binary.ModuleReader;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public abstract class CompilerTestBase {
	@TempDir
	public Path temporaryDirectory;

	public final List<ClassModel> compile(String fixture) throws Exception {
		var wat = temporaryDirectory.resolve(fixture);
		Files.copy(Path.of("src/test/resources/compiler", fixture), wat);
		var wasm = temporaryDirectory.resolve(fixture + ".wasm");
		var log = temporaryDirectory.resolve(fixture + ".log");
		var process = new ProcessBuilder(
			Path.of("../cargo-tools/bin/wasm-tools").toAbsolutePath().toString(),
			"parse",
			wat.toString(),
			"-o",
			wasm.toString()
		).redirectErrorStream(true).redirectOutput(log.toFile()).start();
		try {
			assertTrue(process.waitFor(30, TimeUnit.SECONDS), "wasm-tools timed out");
			assertEquals(0, process.exitValue(), () -> readLog(log));
		}
		finally {
			process.destroyForcibly();
		}
		var compiler = new ModuleCompiler(
			new CompilerOptions(ClassHierarchyResolver.defaultResolver(), "test.generated")
		);
		try(var input = Files.newInputStream(wasm)) {
			var module = new ModuleReader(input).readModule();
			ModuleValidator.validateModule(module);
			compiler.enqueueModule(module, "LargeModule", name -> {
				throw new AssertionError("Unexpected import: " + name);
			});
		}
		var classes = new ArrayList<ClassModel>();
		for(var generator = compiler.dequeueGenerator(); generator != null; generator = compiler.dequeueGenerator()) {
			if(generator instanceof WasmClassGenerator classGenerator) {
				classes.add(ClassFile.of().parse(classGenerator.generate()));
			}
		}
		assertTrue(classes.stream().anyMatch(c -> c.thisClass().asInternalName().equals("test/generated/LargeModule")));
		return classes;
	}

	private static String readLog(Path log) {
		try {
			return Files.readString(log);
		}
		catch(IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}
}
