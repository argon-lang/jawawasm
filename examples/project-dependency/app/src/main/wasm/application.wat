(module
    (import "library" "answer" (func $answer (result i32)))
    (func (export "answer") (result i32)
        call $answer))
