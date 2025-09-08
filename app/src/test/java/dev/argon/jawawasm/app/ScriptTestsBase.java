package dev.argon.jawawasm.app;

import com.google.errorprone.annotations.MustBeClosed;
import dev.argon.jawawasm.app.wast.WastLoader;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Stream;

@Execution(ExecutionMode.CONCURRENT)
abstract class ScriptTestsBase {

	private static final String[] testDirs = {
		"../webassembly-testsuite/proposals/wasm-3.0",
		"../webassembly-testsuite",
		"../additional-tests"
	};

    @TestFactory
	@MustBeClosed
	@SuppressWarnings("StreamResourceLeak")
	Stream<DynamicTest> wastScriptTests() throws IOException {
		var seenFiles = new HashSet<String>();

		return Arrays.stream(testDirs)
			.flatMap(testDir -> {
				try {
					return Files.list(Path.of(testDir));
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			})
			.filter(testFile -> {
				var fileName = testFile.getFileName().toString();

				if(!fileName.equals("table_init.wast")) {
					return false;
				}

				return Files.isRegularFile(testFile) &&
					fileName.endsWith(".wast") &&
					seenFiles.add(fileName);
			})
			.map(this::createTest);
	}


	private DynamicTest createTest(Path testFile) {
		return DynamicTest.dynamicTest(testFile.getFileName().toString(), () -> runWastScript(testFile));
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
