(module
  (func $named (param $wide i64) (param $value-name i32)
    (local $double-value f64) (local $counter i32) (local i32)
    (local.set $counter (local.get $value-name)))
  (func (param i32) (local i32))
)
