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
import java.util.stream.Stream;

@Execution(ExecutionMode.CONCURRENT)
abstract class ScriptTestsBase {

	private static final String[] testDirs = {
		"../webassembly-testsuite/proposals/wasm-3.0",
		"../additional-tests"
	};

    @TestFactory
	@MustBeClosed
	@SuppressWarnings("StreamResourceLeak")
	Stream<DynamicTest> wastScriptTests() throws IOException {
		return Arrays.stream(testDirs)
				.map(Path::of)
				.flatMap(testDir -> {
					try {
						return Files.list(testDir)
							.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".wast"))
							.map(path -> DynamicTest.dynamicTest(testDir.relativize(path).toString(), () -> runWastScript(path)));
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				});
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
