(module
    (import "./lib/left.wasm" "f" (func $left (result i32 i64)))
    (import "../shared/right.wasm" "f" (func $right (result i32 i64)))
    (func (export "f") (result i32 i64)
        call $left
        drop
        drop
        call $right))
