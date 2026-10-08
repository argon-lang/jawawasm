(module
  (type $Record..__name (struct (field $value..__name (mut i32)) (field i64)))
  (type $Numbers--array (array (mut i32)))
  (type $Callback--type (func (param i64)))
  (type $Base--record (sub (struct (field $base-field (mut i32)))))
  (type $Child--record (sub $Base--record (struct (field $child-field (mut i32)) (field $extra-field i64))))
  (func (param (ref null $Record..__name) (ref null $Numbers--array) (ref null $Callback--type)
               (ref null $Base--record) (ref null $Child--record)))
)
