(module
  (tag $error..__name (export "named") (param i32))
  (tag $same-name)
  (tag $same_name)
  (tag (export "unnamed"))
  (func (param i32) (local.get 0) (throw $error..__name))
)
