package dev.argon.jawawasm.gradle;

import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.gradle.testkit.runner.TaskOutcome.*;

class JawaWasmPluginTest {
	@TempDir
	Path projectDir;

	private GradleRunner runner() {
		return GradleRunner.create().withProjectDir(projectDir.toFile()).withPluginClasspath()
			.withArguments("compileWasm", "--configuration-cache", "--stacktrace");
	}

	private void copyFixture(String fixture, String target) throws Exception {
		try(var input = java.util.Objects.requireNonNull(getClass().getResourceAsStream("fixtures/" + fixture))) {
			Files.copy(input, projectDir.resolve(target), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void compileWat(String fixture, String target) throws Exception {
		copyFixture(fixture, fixture);
		var log = projectDir.resolve(target + ".log");
		var process = new ProcessBuilder(
			Path.of("../cargo-tools/bin/wasm-tools").toAbsolutePath().toString(),
			"parse",
			projectDir.resolve(fixture).toString(),
			"-o",
			projectDir.resolve(target).toString()
		).redirectErrorStream(true).redirectOutput(log.toFile()).start();
		try {
			assertTrue(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS), "wasm-tools timed out");
			assertEquals(0, process.exitValue(), Files.readString(log));
		}
		finally {
			process.destroyForcibly();
		}
	}

	private void setup() throws Exception {
		Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name = 'test'");
		copyFixture("multiple-modules.gradle", "build.gradle");
		compileWat("empty.wat", "first.wasm");
		compileWat("empty.wat", "second.wasm");
	}

	@Test
	void convertsMultipleModulesAndReusesConfigurationCache() throws Exception {
		setup();
		var first = runner().build();
		assertEquals(SUCCESS, java.util.Objects.requireNonNull(first.task(":compileWasm")).getOutcome());
		assertTrue(Files.exists(projectDir.resolve("build/generated/wasm/example/wasm/First.class")));
		assertTrue(Files.exists(projectDir.resolve("build/generated/wasm/example/wasm/Other.class")));
		var second = runner().build();
		assertEquals(UP_TO_DATE, java.util.Objects.requireNonNull(second.task(":compileWasm")).getOutcome());
		assertTrue(second.getOutput().contains("Reusing configuration cache."));

		Files.writeString(projectDir.resolve("build/generated/wasm/stale.class"), "stale");
		Files.writeString(
			projectDir.resolve("build.gradle"),
			Files.readString(projectDir.resolve("build.gradle")) + "\nfirstModule.className = 'Renamed'\n"
		);
		var third = runner().build();
		assertEquals(SUCCESS, java.util.Objects.requireNonNull(third.task(":compileWasm")).getOutcome());

		assertFalse(Files.exists(projectDir.resolve("build/generated/wasm/stale.class")));
		assertFalse(Files.exists(projectDir.resolve("build/generated/wasm/example/wasm/First.class")));
		assertTrue(Files.exists(projectDir.resolve("build/generated/wasm/example/wasm/Renamed.class")));
	}

	@Test
	void supportsKotlinDsl() throws Exception {
		setup();
		Files.delete(projectDir.resolve("build.gradle"));
		copyFixture("kotlin-dsl.gradle.kts", "build.gradle.kts");
		assertEquals(SUCCESS, java.util.Objects.requireNonNull(runner().build().task(":compileWasm")).getOutcome());
	}

	@Test
	void sharesMultiValueResultTypesWithModuleLocalImports() throws Exception {
		setup();
		copyFixture("shared-results.gradle", "build.gradle");
		compileWat("producer.wat", "left.wasm");
		compileWat("producer.wat", "right.wasm");
		compileWat("consumer.wat", "consumer.wasm");
		runner().build();
		var output = projectDir.resolve("build/generated/wasm");
		try(var paths = Files.walk(output)) {
			assertEquals(
				1,
				paths.filter(
					path -> java.util.Objects.requireNonNull(path.getFileName()).toString()
						.matches("Result[0-9]+\\.class")
				).count()
			);
		}
		assertTrue(Files.exists(output.resolve("example/shared/Consumer.class")));
		try(var loader = new java.net.URLClassLoader(
			new java.net.URL[]{output.toUri().toURL()},
			getClass().getClassLoader()
		)) {
			var left = loader.loadClass("example.shared.Left");
			var right = loader.loadClass("example.shared.Right");
			var consumer = loader.loadClass("example.shared.Consumer");
			var otherConsumer = loader.loadClass("example.shared.OtherConsumer");
			assertNotNull(consumer.getConstructor(dev.argon.jawawasm.runtime.RuntimeContext.class, left, right));
			assertNotNull(otherConsumer.getConstructor(dev.argon.jawawasm.runtime.RuntimeContext.class, right, left));
			var leftResults = java.util.Arrays.stream(left.getDeclaredMethods())
				.map(java.lang.reflect.Method::getReturnType)
				.filter(type -> type.getSimpleName().matches("Result[0-9]+")).toList();
			assertFalse(leftResults.isEmpty());
			assertTrue(
				java.util.Arrays.stream(right.getDeclaredMethods())
					.anyMatch(method -> leftResults.contains(method.getReturnType()))
			);
			assertTrue(
				java.util.Arrays.stream(consumer.getDeclaredMethods())
					.anyMatch(method -> leftResults.contains(method.getReturnType()))
			);
			assertTrue(
				java.util.Arrays.stream(otherConsumer.getDeclaredMethods())
					.anyMatch(method -> leftResults.contains(method.getReturnType()))
			);
		}
		var cached = runner().build();
		assertEquals(UP_TO_DATE, java.util.Objects.requireNonNull(cached.task(":compileWasm")).getOutcome());
	}

	@Test
	void rejectsUndeclaredImports() throws Exception {
		setup();
		copyFixture("undeclared-imports.gradle", "build.gradle");
		compileWat("producer.wat", "left.wasm");
		compileWat("producer.wat", "right.wasm");
		compileWat("consumer.wat", "consumer.wasm");
		var result = runner().buildAndFail();
		assertTrue(
			result.getOutput().contains("No mapping configured for import './lib/left.wasm' in module Consumer")
		);
	}

	@Test
	void rejectsUnknownImportTargets() throws Exception {
		setup();
		Files.writeString(
			projectDir.resolve("build.gradle"),
			"\nfirstModule.imports.put('path', objects.newInstance(dev.argon.jawawasm.gradle.WasmModule, 'Missing'))\n",
			java.nio.file.StandardOpenOption.APPEND
		);
		assertTrue(
			runner().buildAndFail().getOutput().contains("Unregistered import target 'Missing' for module First")
		);
	}

	@Test
	void rejectsExplicitImportCycles() throws Exception {
		setup();
		copyFixture("cycle-imports.gradle", "imports.gradle");
		Files.writeString(
			projectDir.resolve("build.gradle"),
			Files.readString(projectDir.resolve("imports.gradle")),
			java.nio.file.StandardOpenOption.APPEND
		);
		assertTrue(runner().buildAndFail().getOutput().contains("Cyclic WebAssembly module imports"));
	}

	@Test
	void compilesAndPackagesJavaHostsAutomatically() throws Exception {
		setup();
		copyFixture("java-host.gradle", "build.gradle");
		Files.createDirectories(projectDir.resolve("src/wasmHost/java/example"));
		copyFixture("LocalHost.java", "src/wasmHost/java/example/LocalHost.java");
		Files.createDirectories(projectDir.resolve("src/wasmHost/resources/example"));
		Files.writeString(projectDir.resolve("src/wasmHost/resources/example/host.txt"), "host resource");
		Files.createDirectories(projectDir.resolve("src/main/java/example"));
		copyFixture("Main.java", "src/main/java/example/Main.java");
		compileWat("java-host.wat", "consumer.wasm");
		var runtime = Path
			.of(
				java.util.Objects.requireNonNull(
					java.util.Objects.requireNonNull(
						dev.argon.jawawasm.runtime.ResultVoid.class.getProtectionDomain().getCodeSource()
					).getLocation()
				).toURI()
			);
		var result = runner().withArguments("jar", "--configuration-cache", "--stacktrace", "-PruntimeJar=" + runtime)
			.build();
		assertEquals(SUCCESS, java.util.Objects.requireNonNull(result.task(":compileWasmHostJava")).getOutcome());
		assertEquals(SUCCESS, java.util.Objects.requireNonNull(result.task(":compileWasm")).getOutcome());
		assertTrue(Files.exists(projectDir.resolve("build/classes/java/main/example/LocalHost.class")));
		try(var jar = new java.util.jar.JarFile(projectDir.resolve("build/libs/test.jar").toFile())) {
			assertNotNull(jar.getEntry("example/LocalHost.class"));
			assertNotNull(jar.getEntry("example/host.txt"));
			assertNotNull(jar.getEntry("example/Main.class"));
			assertNotNull(jar.getEntry("example/generated/Consumer.class"));
		}
		var cached = runner().withArguments("jar", "--configuration-cache", "--stacktrace", "-PruntimeJar=" + runtime)
			.build();
		assertTrue(cached.getOutput().contains("Reusing configuration cache."));
		assertEquals(UP_TO_DATE, java.util.Objects.requireNonNull(cached.task(":compileWasm")).getOutcome());
	}

	@Test
	void recompilesHostsWithMainModuleAccessChecks() throws Exception {
		setup();
		Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name = 'test'; include 'library'");
		Files.createDirectories(projectDir.resolve("library/src/main/java/hidden"));
		Files.writeString(projectDir.resolve("library/build.gradle"), "plugins { id 'java-library' }");
		var descriptor = projectDir.resolve("library/src/main/java/module-info.java");
		Files.writeString(descriptor, "module hidden.library {}\n");
		Files.writeString(
			projectDir.resolve("library/src/main/java/hidden/Secret.java"),
			"package hidden; public class Secret {}\n"
		);
		Files.writeString(
			projectDir.resolve("build.gradle"),
			"\ndependencies { implementation project(':library') }\n",
			java.nio.file.StandardOpenOption.APPEND
		);
		Files.createDirectories(projectDir.resolve("src/wasmHost/java/example"));
		Files.writeString(
			projectDir.resolve("src/wasmHost/java/example/LocalHost.java"),
			"package example; public class LocalHost { public hidden.Secret secret; }\n"
		);
		Files.createDirectories(projectDir.resolve("src/main/java"));
		Files.writeString(
			projectDir.resolve("src/main/java/module-info.java"),
			"module example.application { requires hidden.library; }\n"
		);
		var result = runner().withArguments("jar", "--configuration-cache", "--stacktrace").buildAndFail();
		assertEquals(SUCCESS, java.util.Objects.requireNonNull(result.task(":compileWasmHostJava")).getOutcome());
		assertEquals(FAILED, java.util.Objects.requireNonNull(result.task(":compileJava")).getOutcome());
		assertTrue(result.getOutput().contains("does not export"));

		Files.writeString(descriptor, "module hidden.library { exports hidden; }\n");
		var fixed = runner().withArguments("jar", "--configuration-cache", "--stacktrace").build();
		assertEquals(SUCCESS, java.util.Objects.requireNonNull(fixed.task(":compileJava")).getOutcome());
		assertTrue(Files.exists(projectDir.resolve("build/classes/java/main/example/LocalHost.class")));
	}

	@Test
	void patchesGeneratedClassesIntoMainModule() throws Exception {
		setup();
		Files.createDirectories(projectDir.resolve("src/main/java/example"));
		var descriptor = projectDir.resolve("src/main/java/module-info.java");
		Files.writeString(
			descriptor,
			"/* module misleading.name {} */ open module example.application { exports example.wasm; }\n"
		);
		Files.writeString(
			projectDir.resolve("src/main/java/example/Main.java"),
			"package example; public class Main { public Class<?> type() { return example.wasm.First.class; } }\n"
		);
		var result = runner().withArguments("jar", "--configuration-cache", "--stacktrace").build();
		assertEquals(SUCCESS, java.util.Objects.requireNonNull(result.task(":compileJava")).getOutcome());
		try(var jar = new java.util.jar.JarFile(projectDir.resolve("build/libs/test.jar").toFile())) {
			assertNotNull(jar.getEntry("module-info.class"));
			assertNotNull(jar.getEntry("example/wasm/First.class"));
		}
		var cached = runner().withArguments("jar", "--configuration-cache", "--stacktrace").build();
		assertTrue(cached.getOutput().contains("Reusing configuration cache."));
		assertEquals(UP_TO_DATE, java.util.Objects.requireNonNull(cached.task(":compileJava")).getOutcome());
		Files.writeString(descriptor, "module example.renamed { exports example.wasm; }\n");
		var renamed = runner().withArguments("jar", "--configuration-cache", "--stacktrace").build();
		assertEquals(SUCCESS, java.util.Objects.requireNonNull(renamed.task(":compileJava")).getOutcome());
	}

	@Test
	void consumesSeparatelyCompiledWasmFromProjectDependency() throws Exception {
		Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name = 'test'; include 'library', 'app'");
		Files.createDirectories(projectDir.resolve("library/src/main/java/example/library"));
		Files.createDirectories(projectDir.resolve("app/src/main/java/example"));
		copyFixture("dependency-library.gradle", "library/build.gradle");
		copyFixture("dependency-app.gradle", "app/build.gradle");
		copyFixture("LibraryHost.java", "library/src/main/java/example/library/LibraryHost.java");
		copyFixture("DependencyMain.java", "app/src/main/java/example/Main.java");
		compileWat("dependency-library.wat", "library/library.wasm");
		compileWat("dependency-app.wat", "app/application.wasm");
		var runtime = Path
			.of(
				java.util.Objects.requireNonNull(
					java.util.Objects.requireNonNull(
						dev.argon.jawawasm.runtime.ResultVoid.class.getProtectionDomain().getCodeSource()
					).getLocation()
				).toURI()
			);
		var build = runner()
			.withArguments(":app:jar", ":app:run", "--configuration-cache", "--stacktrace", "-PruntimeJar=" + runtime);
		var result = build.build();
		assertEquals(SUCCESS, java.util.Objects.requireNonNull(result.task(":library:compileWasm")).getOutcome());
		assertEquals(SUCCESS, java.util.Objects.requireNonNull(result.task(":app:compileWasm")).getOutcome());
		assertTrue(result.getOutput().contains("\n42\n"));
		try(var jar = new java.util.jar.JarFile(projectDir.resolve("library/build/libs/library.jar").toFile())) {
			assertNotNull(jar.getEntry("example/library/Library.class"));
			assertNotNull(jar.getEntry("example/library/LibraryHost.class"));
		}
		try(var jar = new java.util.jar.JarFile(projectDir.resolve("app/build/libs/app.jar").toFile())) {
			assertNotNull(jar.getEntry("example/generated/Application.class"));
			assertNull(jar.getEntry("example/library/Library.class"));
		}
		var cached = build.build();
		assertTrue(cached.getOutput().contains("Reusing configuration cache."));
		assertEquals(UP_TO_DATE, java.util.Objects.requireNonNull(cached.task(":library:compileWasm")).getOutcome());
		assertEquals(UP_TO_DATE, java.util.Objects.requireNonNull(cached.task(":app:compileWasm")).getOutcome());
		assertTrue(cached.getOutput().contains("\n42\n"));
	}

	@Test
	void rejectsInvalidWasm() throws Exception {
		setup();
		Files.writeString(projectDir.resolve("first.wasm"), "invalid");
		var result = runner().buildAndFail();
		assertEquals(FAILED, java.util.Objects.requireNonNull(result.task(":compileWasm")).getOutcome());
	}
}
