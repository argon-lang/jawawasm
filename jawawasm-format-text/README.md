# jawawasm-format-text

Rust text parser for JawaWasm with a C ABI, intended to be compiled to
WebAssembly and later translated to JVM bytecode by `AOTEngine`.
WAST conversion uses `json-from-wast`; WAT encoding uses its `wast` dependency.
Both versions match the repository's `wasm-tools` pin.

```sh
cargo build --manifest-path jawawasm-format-text/Cargo.toml --locked --release --target wasm32-unknown-unknown
cargo test --manifest-path jawawasm-format-text/Cargo.toml --locked
```

The Wasm output is
`target/wasm32-unknown-unknown/release/jawawasm_format_text.wasm` inside this
crate. It imports only the three host callbacks from `callback`; it needs no
WASI filesystem or process facilities.

Building the Gradle `format` project runs Cargo automatically (placing its
output in `format/build/cargo`), then compiles the Wasm with `AOTEngine`.
The generated JVM classes and data resources are bundled in the format JAR
and implement `dev.argon.jawawasm.format.text.ModuleReader` and
`dev.argon.jawawasm.format.wast.WastScriptParser`. The script parser deserializes
the reported JSON and retains the referenced files in memory.

The exports use the C calling convention. On `wasm32-unknown-unknown`, pointers,
the opaque context, and lengths are all Wasm `i32` values:

```text
jawawasm_allocate(len: usize) -> *mut u8
jawawasm_free(buff: *mut u8, len: usize)
jawawasm_parse_wat(ctx: *mut c_void, buff: *const u8, len: usize)
jawawasm_parse_wast(ctx: *mut c_void, buff: *const u8, len: usize)
```

The host provides these imports in the `callback` module, all returning void:

```text
jawawasm_report_error(ctx, message, len)
jawawasm_report_module(ctx, id, id_len, buff, buff_len)
jawawasm_report_json(ctx, buff, len)
```

Allocate input with `jawawasm_allocate(len)`, copy UTF-8 bytes into it, call a
parse function, then call `jawawasm_free(buff, len)` with the original length.
The opaque context is forwarded unchanged. Copy callback buffers before
returning; they are not transferred to the host and must not be freed by it.
Allocation failure or a Rust panic traps/aborts; ordinary parse errors use the
error callback.

Buffers use the global allocator directly and are zero-initialized.
A zero-length allocation returns null, and freeing null is a no-op. Null input
is allowed for zero bytes; nonempty input must remain valid and unchanged until
the parse call returns. Callback strings are UTF-8 without NUL terminators.
Callbacks must not modify borrowed buffers or unwind through the parser.
Conversion errors report one error callback and no module or JSON callbacks.

`jawawasm_parse_wat` reports one binary module, using the WAT identifier without
`$` (or an empty identifier). `jawawasm_parse_wast` reports every generated file
under its JSON filename, then reports the JSON document. The synthetic source
filename is `input.wast`. A reported WAST file can contain quoted text or an
intentionally malformed binary; consult the JSON's `module_type` and
`binary_filename`. Calls have independent filename namespaces, so the host
should collect files per parse call/context.
