;; Preserve a value below wide operands and consume the low/high results in order.
(module
  (func (export "add") (param i64 i64 i64 i64) (result i32 i64 i64)
    (local i64)
    i32.const 42
    local.get 0
    local.get 1
    local.get 2
    local.get 3
    i64.add128
    local.set 4
    i64.const 1
    i64.add
    local.get 4)
  (func (export "sub") (param i64 i64 i64 i64) (result i32 i64 i64)
    i32.const 42
    local.get 0
    local.get 1
    local.get 2
    local.get 3
    i64.sub128)
  (func (export "mul-s") (param i64 i64) (result i32 i64 i64)
    i32.const 42
    local.get 0
    local.get 1
    i64.mul_wide_s)
  (func (export "mul-u") (param i64 i64) (result i32 i64 i64)
    i32.const 42
    local.get 0
    local.get 1
    i64.mul_wide_u)
  (func (export "chain") (result i64 i64)
    i64.const -1
    i64.const -1
    i64.mul_wide_u
    i64.const -1
    i64.const 0
    i64.add128
    i64.const 0
    i64.const -1
    i64.sub128)
  ;; Validation must accept stack-polymorphic wide instructions.
  (func (result i64 i64)
    unreachable
    i64.add128)
  (func (result i64 i64)
    unreachable
    i64.sub128)
  (func (result i64 i64)
    unreachable
    i64.mul_wide_s)
  (func (result i64 i64)
    unreachable
    i64.mul_wide_u)
)

;; Crossing the signed boundary does not carry or borrow in unsigned arithmetic.
(assert_return (invoke "add"
  (i64.const 0x7fffffffffffffff)
  (i64.const 5)
  (i64.const 1)
  (i64.const 6))
  (i32.const 42)
  (i64.const 0x8000000000000001)
  (i64.const 11))
(assert_return (invoke "sub"
  (i64.const 0x8000000000000000)
  (i64.const 5)
  (i64.const 1)
  (i64.const 6))
  (i32.const 42)
  (i64.const 0x7fffffffffffffff)
  (i64.const -1))
;; Carry and borrow wrap the high half modulo 2^64.
(assert_return (invoke "add"
  (i64.const -1)
  (i64.const -1)
  (i64.const 1)
  (i64.const 0))
  (i32.const 42)
  (i64.const 1)
  (i64.const 0))
(assert_return (invoke "sub"
  (i64.const 0)
  (i64.const 0)
  (i64.const 1)
  (i64.const 0))
  (i32.const 42)
  (i64.const -1)
  (i64.const -1))
(assert_return (invoke "mul-s" (i64.const 0x8000000000000000)
  (i64.const -1))
  (i32.const 42)
  (i64.const 0x8000000000000000)
  (i64.const 0))
(assert_return (invoke "mul-u" (i64.const -1)
  (i64.const -1))
  (i32.const 42)
  (i64.const 1)
  (i64.const -2))
(assert_return (invoke "chain")
  (i64.const 0)
  (i64.const 0))
