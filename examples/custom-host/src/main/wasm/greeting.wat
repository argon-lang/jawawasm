(module
    (import "console" "print" (func $print (param i32)))
    (func (export "greet")
        i32.const 42
        call $print))
