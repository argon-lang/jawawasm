(module
  (type $s (struct (field (mut i8))))
  (import "host" "make" (func $make (result (ref $s))))
  (func (export "run") (result i32) (local $s (ref null $s))
    call $make local.set $s
    local.get $s i32.const 42 struct.set $s 0
    local.get $s struct.get_u $s 0))
