package dev.argon.jawawasm.app;

import com.google.errorprone.annotations.MustBeClosed;
import dev.argon.jawawasm.app.wast.WastLoader;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

@Execution(ExecutionMode.CONCURRENT)
abstract class ScriptTestsBase {

	private final List<String> excludedTests = List.of(new String[] {
		// Exclude tests for the text format.
		"id.wast",
		"annotations.wast",
		"inline-module.wast",

	});

	private static final String testDir = "../webassembly-spec/test/core";

    @TestFactory
	@MustBeClosed
	Stream<DynamicTest> wastScriptTests() throws IOException {
		var testPath = Path.of(testDir);
		return Files.walk(testPath)
				.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".wast") && !isExcludedTest(testPath.relativize(path)))
				.map(path -> DynamicTest.dynamicTest(testPath.relativize(path).toString(), () -> runWastScript(path)));
	}

	private boolean isExcludedTest(Path path) {
		if(excludedTests.contains(path.toString().replace('\\', '/'))) {
			return true;
		}

		var parent = path.getParent();
		if(parent != null) {
			if(excludedTests.contains(parent.toString().replace(File.pathSeparator, "/") + "/")) {
				return true;
			}
		}

//		if(!path.toString().equals("gc/extern.wast")) {
//			return true;
//		}

		return false;
	}

	protected abstract ScriptExecutor<?> createScriptExecutor(WastLoader loader);

	private void runWastScript(Path path) throws Throwable {
		var loader = new WastLoader(Path.of("../cargo-tools/bin/wasm-tools").toAbsolutePath());

		try(var scriptLoaded = loader.loadScript(path)) {
			try(var interpreter = createScriptExecutor(loader)) {
				interpreter.initialize();
				interpreter.executeScript(scriptLoaded);
			}
		}

	}
}
