;; An already populated initialization bitset must stay isolated across scopes.
(module
  (func (export "initialized-before-block") (param $p (ref extern)) (result (ref extern))
    (local $a (ref extern)) (local $b (ref extern))
    (local.set $a (local.get $p))
    (block
      (local.set $b (local.get $a))
      (drop (local.get $b)))
    (local.get $a)))
(assert_return (invoke "initialized-before-block" (ref.extern 1)) (ref.extern 1))

(assert_invalid
  (module
    (func (param $p (ref extern))
      (local $a (ref extern)) (local $b (ref extern))
      (local.set $a (local.get $p))
      (block (local.set $b (local.get $a)))
      (drop (local.get $b))))
  "uninitialized local")

(assert_invalid
  (module
    (func (param $p (ref extern))
      (local $a (ref extern)) (local $b (ref extern))
      (local.set $a (local.get $p))
      (if (i32.const 0)
        (then (local.set $b (local.get $a)))
        (else (drop (local.get $b))))))
  "uninitialized local")
