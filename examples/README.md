# Gradle plugin examples

Each directory is an independent Gradle build using JawaWasm 0.3.1.
Run Gradle on Java 24 or later; use the repository wrapper or Gradle 9.6.1 or later.
The builds request a Java 24 toolchain, which must be installed locally.

From the repository root:

```sh
./gradlew -p examples/basic jar
./gradlew -p examples/custom-host jar
./gradlew -p examples/project-dependency :app:jar
```

These commands use the published Plugin Portal plugin and Maven Central runtime.
Before the release is available, add `-PlocalJawaWasm` to build the plugin and
libraries from this checkout instead. No local Maven publication is needed.

## Basic compilation

`basic` builds `answer.wat` into a Wasm binary and compiles it into `example.generated.Answer`. The Java source
references that generated type. `jar` automatically runs `compileWasm` and
includes the generated classes and resources. You can also run `compileWasm`
directly to inspect output under `build/generated/wasm`.

## Dependency built in another project

`project-dependency` contains `library` and `app` subprojects. Both apply the
JawaWasm plugin. `library` converts its WAT to Wasm, compiles it into
`example.library.Library`, and packages the generated JVM classes in its JAR.
It exposes the JawaWasm runtime as an API dependency.

The library also supplies `LibraryHost`, a small Java adapter whose `@WasmExport`
method delegates to the generated library class. The host import API discovers
these annotations; generated module methods currently do not carry them.

`app` uses `implementation(project(":library"))` and registers
`example.library.LibraryHost` with `hostModules`. Its Wasm module's
exact `library` import string maps to that host entry. Gradle builds the library
before the application's AOT compilation. Each project compiles its own Wasm;
the application JAR contains only its own generated module. The library remains
a normal runtime dependency. Run `:app:run` to invoke the application, which calls
through the adapter into the compiled library and prints `42`.

## Custom Java host module

`custom-host` maps the Wasm import module `console` to `example.ConsoleHost`.
The host's `@WasmExport` method matches the imported `print(i32)` function;
`ResultVoid` represents its empty result. Put hosts in `src/wasmHost/java` so
Gradle compiles them before AOT compilation, then recompiles them with main
sources to enforce the main module descriptor's access rules. Hosts and generated Wasm classes
are both available to main Java sources and included in the JAR.
Host code must not depend on main Java sources or generated Wasm classes.

## Wasm inputs

Each build uses the text `ModuleReader` through a `WatCompile` task to generate its
binary inputs from checked-in `.wat` sources. Binaries are written under
`build/wasm`; none are checked in. Task-backed file providers make `compileWasm`
run the WAT tasks automatically, and Gradle tracks source changes to rebuild
only when needed. `clean` removes the generated binaries.

To build all examples locally, run:

```sh
./gradlew -p examples/basic -PlocalJawaWasm jar
./gradlew -p examples/custom-host -PlocalJawaWasm jar
./gradlew -p examples/project-dependency -PlocalJawaWasm :app:jar
```
