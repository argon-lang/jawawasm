# JawaWasm

JawaWasm is an experimental WebAssembly engine written in Java.
It compiles to Java Bytecode.
It also passes the entire WebAssembly 3.0 test suite.

The `runtime` library contains types that are needed to execute WebAssembly.
This includes types that represent WebAssembly values such as V128,
types used for module imports and exports, and
methods that implement WebAssembly operations.

The `format-core` library contains the WebAssembly model and binary reader.
The engine depends on this core. The `format` library exposes the core transitively
and provides a WAT module reader and a WAST script parser.

The `engine` contains three different compilation types of engines.
The `AOTEngine` compiles WebAssembly to Java Bytecode.
The `ReflectionEngine` compiles WebAssembly to Java Bytecode on demand, allowing execution on demand.
The interpreter Engine uses interpretation without generating Java Bytecode.


## Gradle plugin

See [the Gradle examples](examples/README.md) for standalone builds covering basic
compilation, a Wasm dependency built by another project, and a custom Java host.

The `gradle-plugin` project provides the `dev.argon.jawawasm` plugin. It requires
Gradle running on Java 24 or later. After publishing the plugin and its dependencies
to your Maven repository, add that repository to `pluginManagement.repositories`
and apply the plugin:

```kotlin
plugins {
    id("dev.argon.jawawasm") version "0.3.1"
}

wasm {
    packageName.set("example.wasm")
    val first = module("First") {
        source.set(layout.projectDirectory.file("src/main/wasm/first.wasm"))
        // className defaults to the entry name (First).
    }
    module("Second") {
        source.set(layout.projectDirectory.file("src/main/wasm/second.wasm"))
        className.set("SecondModule")
        imports.put("./first.wasm", first)
    }
}
```

Run `compileWasm` to convert all configured binary Wasm files in one shared
`AOTEngine`. Each module explicitly lists its `imports`: keys are the exact
import module strings in that Wasm file, and values are `WasmModule` or
`HostModule` objects (or providers of those objects). For example, Second's import
`./first.wasm` resolves to First. Each module has its own mapping, so the same
import path can refer to different targets in different modules. Names and paths
are never inferred or normalized. Every import must have a mapping; unknown
targets and dependency cycles are rejected. Dependencies compile before their
importers. Java hosts use the shared `classpath`.

All modules share generated result types, a package, and the output directory
`build/generated/wasm` (override it with `outputDirectory`). The task validates
every input and replaces its output directory on recompilation. A change to any
module recompiles the set, preserving consistent shared types even when Gradle
uses its build cache or configuration cache.
Provider-backed sources and classpaths retain their producer task dependencies.

Generated classes and resources are automatically available to main Java sources
and bundled in the main JAR. Add the runtime dependency:

```kotlin
dependencies {
    implementation("dev.argon.jawawasm:jawawasm-runtime:0.3.1")
}
```

The plugin applies Java and creates a `wasmHost` source set automatically. Put Java
host classes in `src/wasmHost/java` and host resources in `src/wasmHost/resources`.
Host compilation and AOT lookup use the main compile classpath captured before
generated Wasm outputs are added. Declare their dependencies with the
usual `implementation` or `api` configuration. Gradle compiles the hosts
before `compileWasm`, then recompiles their sources with main sources so the main
module descriptor enforces Java module access rules. Host resources are included
in main resources. Host classes and resources are bundled in the main JAR and
are available to main Java sources.

```kotlin
dependencies {
    implementation("dev.argon.jawawasm:jawawasm-runtime:0.3.1")
    // Java host classes supplied by a library use the same configuration:
    // implementation("com.example:wasm-hosts:1.0")
}
wasm {
    val host = hostModules.create("Host") {
        className.set("example.host.HostModule")
    }
    module("WithHost") {
        source.set(layout.projectDirectory.file("src/main/wasm/with-host.wasm"))
        imports.put("env", host)
    }
}
```

The host source set compiles independently of main sources, avoiding a cycle
when main Java code uses the generated Wasm classes. Host code must not depend
on main sources or generated Wasm classes.

Generated classes require the JawaWasm runtime and the Java version used for
compilation. For modular Java projects, the plugin automatically adds `--patch-module` to
main Java compilation using the module name in `module-info.java`, so main
sources can reference generated classes.

## Setup

If using SDKMAN, run `sdk env` to use the expected versions of Java and Gradle.

Building `format` also requires Rust 1.88 or later with the
`wasm32-unknown-unknown` target installed (`rustup target add
wasm32-unknown-unknown`). Gradle runs Cargo with the checked-in lockfile and
then invokes `AOTEngine` automatically. The format facade requires Java 24;
`format-core` and `runtime` continue to target Java 22.

Java formatting uses Spotless with the settings in `eclipse-formatter.properties`.
Run `./gradlew spotlessApply` to format sources or `./gradlew spotlessCheck` to check
formatting. Formatting is also checked by `./gradlew check`.

To run the tests for this project, submodules need to be checked out.
The application and its tests use the compiled WAST parser directly. WAT
fixtures, examples, and the reflection driver use the text ModuleReader.
To perform this setup, run the script `setup-tests.sh`.
This script initializes the test suite submodule.
