(module
  (export "block_branch_i32x4.replace_lane" (func $"block_branch_i32x4.replace_lane"))
  (export "block_branch_i64.extend_i32_u" (func $"block_branch_i64.extend_i32_u"))

  (func $"block_branch_i32x4.replace_lane" (param i32 i32 i32 i32) (result i32)
    loop
      v128.const i32x4 0x9e3779b9 0x0000000b 0x00000000 0x00000000
      i32.const 0
      i32x4.replace_lane 1
      drop
      i32.const 4
      i32.const 2
      i32.gt_u
      br_if 0
    end
    i32.const 0
  )


  (func $"block_branch_i64.extend_i32_u" (param)
    block
      i64.const 0
      i32.const 0
      i64.extend_i32_u
      i32.const 0
      select
      drop
    end
  )
)
