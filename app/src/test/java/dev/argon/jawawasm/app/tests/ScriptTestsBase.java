package dev.argon.jawawasm.app.tests;

import com.google.errorprone.annotations.MustBeClosed;
import dev.argon.jawawasm.app.ScriptExecutor;
import dev.argon.jawawasm.format.wast.WastScriptParser;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Stream;

@Execution(ExecutionMode.CONCURRENT)
public abstract class ScriptTestsBase {

	private static final String[] testDirs = {"../webassembly-testsuite",
			"../webassembly-testsuite/proposals/compact-import-section",
			"../webassembly-testsuite/proposals/wide-arithmetic", "../additional-tests"};

	@TestFactory
	@MustBeClosed
	@SuppressWarnings("StreamResourceLeak")
	public Stream<DynamicTest> wastScriptTests() throws IOException {
		var seenFiles = new HashSet<String>();

		return Arrays.stream(testDirs).flatMap(testDir -> {
			try {
				return Files.list(Path.of(testDir));
			}
			catch(IOException e) {
				throw new UncheckedIOException(e);
			}
		}).filter(testFile -> {
			var fileName = Objects.requireNonNull(testFile.getFileName()).toString();

			return Files.isRegularFile(testFile) && fileName.endsWith(".wast") && seenFiles.add(fileName);
		}).map(this::createTest);
	}

	private DynamicTest createTest(Path testFile) {
		return DynamicTest
			.dynamicTest(Objects.requireNonNull(testFile.getFileName()).toString(), () -> runWastScript(testFile));
	}

	protected abstract ScriptExecutor<?> createScriptExecutor();

	private void runWastScript(Path path) throws Throwable {
		try(var reader = Files.newBufferedReader(path)) {
			var scriptLoaded = new WastScriptParser(reader, path.toString()).parseScript();
			try(var interpreter = createScriptExecutor()) {
				interpreter.initialize();
				interpreter.executeScript(scriptLoaded);
			}
		}
	}
}
