# Direct V128 tests

The generated Java classes contain direct V128 operation calls with embedded
input bits and expected results derived from the checked-in WebAssembly SIMD
suite. Running them does not read WAST files, invoke wasm-tools, compile Wasm,
or instantiate an engine.

Regenerate from the repository root:

```sh
./gradlew :runtime:generateV128Tests
```

Gradle runs the Java generator automatically before compiling tests and writes
sources to `runtime/build/generated/sources/v128Test/java`. The generator uses
only the Java standard library. Floating-point literals are
converted with exact rational arithmetic and round-to-nearest, ties-to-even;
f32 values are not converted through double precision. Assertions preserve
signed zero, exact payload bits, and canonical/arithmetic NaN expectations.
Each generated assertion records its originating WAST file and line.

Run all runtime tests or isolate an operation:

```sh
./gradlew :runtime:test
./gradlew :runtime:test --tests '*.V128I8x16ArithTest.i8x16_add'
./gradlew :runtime:test --tests '*.V128F32x4ArithTest.f32x4_add*'
```

Large operation groups are split into methods of at most 100 source cases.
These tests provide a correctness baseline and focused call sites for JIT
inspection. Their execution times include assertions and input construction;
use a warmed-up benchmark harness for throughput/allocation comparisons.

Coverage includes direct arithmetic, comparisons, saturation, shifts, bitwise
and boolean operations, floating-point rounding and min/max, and first/last
integer lane extraction/replacement. Compound exports, malformed modules,
and operations not selected by the generator remain covered by the application
specification tests. Additional construction tests check lane order, raw float
bits, and invalid extraction indices before changing the V128 representation.
