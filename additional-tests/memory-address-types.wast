;; Copy between memories with different address types in both directions.
(module
  (memory $m32 1)
  (memory $m64 i64 1)
  (data (memory $m32) (i32.const 0) "\01\02\03\04")
  (data (memory $m64) (i64.const 0) "\05\06\07\08")
  (func (export "load32") (param i32) (result i32)
    (i32.load $m32 (local.get 0)))
  (func (export "load64") (param i64) (result i32)
    (i32.load $m64 (local.get 0)))
  (func (export "copy32to64") (param i64 i32 i32)
    (memory.copy $m64 $m32 (local.get 0) (local.get 1) (local.get 2)))
  (func (export "copy64to32") (param i32 i64 i32)
    (memory.copy $m32 $m64 (local.get 0) (local.get 1) (local.get 2))))

(invoke "copy32to64" (i64.const 8) (i32.const 0) (i32.const 4))
(invoke "copy64to32" (i32.const 8) (i64.const 0) (i32.const 4))
(assert_return (invoke "load64" (i64.const 8)) (i32.const 0x04030201))
(assert_return (invoke "load32" (i32.const 8)) (i32.const 0x08070605))

;; A 64-bit operand must retain its high bits and trap before changing either memory.
(assert_trap
  (invoke "copy32to64" (i64.const 0x100000000) (i32.const 0) (i32.const 4))
  "out of bounds memory access")
(assert_trap
  (invoke "copy64to32" (i32.const 0) (i64.const 0x100000000) (i32.const 4))
  "out of bounds memory access")
(assert_return (invoke "load64" (i64.const 0)) (i32.const 0x08070605))
(assert_return (invoke "load32" (i32.const 0)) (i32.const 0x04030201))
