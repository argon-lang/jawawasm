# JawaWasm

JawaWasm is an experimental WebAssembly engine written in Java.
It compiles to Java Bytecode.
It also passes the entire WebAssembly 3.0 test suite.

The `format-core` library contains the WebAssembly model and binary reader.
The engine depends on this core. The `format` library exposes the core transitively
and provides a WAT module reader and a WAST script parser. Its Rust parser is compiled to WebAssembly,
then to JVM classes using the engine during the build. The generated classes
and data resources are bundled in `format`; parsing at runtime needs no Rust
toolchain or engine dependency.
Parsed WAST scripts keep the files referenced by their commands in memory.

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

In order to run the tests for this project, submodules need to be checked out and built.
The application and its tests use the compiled WAST parser directly. The
compiler fixtures and reflection driver still use wasm-tools during setup.
To perform this setup, run the script `setup-tests.sh`.
This script will set up the submodule and build the tools needed to run the test suite.
If you run into issues building, see the [reference interpreter README](https://github.com/WebAssembly/spec/tree/main/interpreter#building).
