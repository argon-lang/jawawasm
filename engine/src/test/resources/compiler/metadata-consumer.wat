(module
  (rec
    (type $s (sub (struct (field (mut i8)) (field (ref null $s)) (field (ref null extern)))))
    (type $child (sub final $s (struct (field (mut i8)) (field (ref null $s)) (field (ref null extern)) (field i64)))))
  (type $a (sub (array (mut i16))))
  (type $b (sub final $a (array (mut i16))))
  (type $identity (func (param (ref null $s)) (result (ref null $s))))
  (import "p" "make" (func $make (result (ref $s))))
  (import "p" "array" (func $array (result (ref $a))))
  (import "p" "identity" (func $id (type $identity)))
  (import "p" "memory" (memory 1 2))
  (import "p" "table" (table 1 3 (ref null $identity)))
  (import "p" "global" (global $g (mut (ref null $s))))
  (import "p" "extern" (global (ref null extern)))
  (import "p" "tag" (tag $tag (param i32)))
  (export "make-again" (func $make))
  (export "memory-again" (memory 0))
  (export "table-again" (table 0))
  (export "global-again" (global $g))
  (export "tag-reexport" (tag $tag))
  (func (export "run") (result i32)
    (local $s (ref null $s)) (local $a (ref null $a))
    call $make call $id local.tee $s global.set $g
    local.get $s i32.const 42 struct.set $s 0
    call $array local.tee $a i32.const 0 i32.const 1 array.set $a
    global.get $g struct.get_u $s 0
    local.get $a i32.const 0 array.get_u $a i32.add
    memory.size i32.add
    table.size 0 i32.add)
)
