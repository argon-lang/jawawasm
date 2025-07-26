
(module
(func (export "myfunc") (param i32) (result i32)
    (local i32 i32 i32 i32)
    block ;; label = @1
      block ;; label = @2
        block ;; label = @3
          block ;; label = @4
            block ;; label = @5
              block ;; label = @6
                local.get 0
                i32.const 4
                i32.lt_u
                br_if 0 (;@6;)
                local.get 0
                f64.convert_i32_u
                f64.sqrt
                i32.trunc_sat_f64_u
                local.tee 1
                i32.eqz
                br_if 1 (;@5;)
                local.get 1
                local.get 0
                local.get 1
                i32.div_u
                local.get 1
                i32.add
                i32.const 1
                i32.shr_u
                local.tee 2
                i32.lt_u
                br_if 2 (;@4;)
                local.get 2
                local.set 3
                br 3 (;@3;)
              end
              local.get 0
              i32.const 0
              i32.ne
              local.set 1
              br 3 (;@2;)
            end
            unreachable
          end
          loop ;; label = @4
            local.get 2
            local.get 0
            local.get 2
            i32.div_u
            local.get 2
            i32.add
            i32.const 1
            i32.shr_u
            local.tee 3
            i32.lt_u
            local.set 4
            local.get 2
            local.set 1
            local.get 3
            local.set 2
            local.get 4
            br_if 0 (;@4;)
          end
        end
        local.get 1
        local.get 3
        i32.le_u
        br_if 0 (;@2;)
        loop ;; label = @3
          local.get 3
          local.tee 1
          i32.eqz
          br_if 2 (;@1;)
          local.get 1
          local.get 0
          local.get 1
          i32.div_u
          local.get 1
          i32.add
          i32.const 1
          i32.shr_u
          local.tee 3
          i32.gt_u
          br_if 0 (;@3;)
        end
      end
      local.get 1
      return
    end
    unreachable
  )

)
