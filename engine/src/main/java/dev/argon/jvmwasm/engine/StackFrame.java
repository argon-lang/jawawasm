package dev.argon.jvmwasm.engine;

import dev.argon.jvmwasm.format.data.V128;
import dev.argon.jvmwasm.format.instructions.*;
import dev.argon.jvmwasm.format.modules.Func;
import dev.argon.jvmwasm.format.modules.LabelIdx;
import dev.argon.jvmwasm.format.modules.MemIdx;
import dev.argon.jvmwasm.format.types.FuncType;
import dev.argon.jvmwasm.format.types.ResultType;

import java.math.BigDecimal;
import java.util.*;

class StackFrame {

	public StackFrame(InstantiatedModule module, Func func, Object[] args) {
		this.module = module;
		block = func.body().body();
		topBlockType = module.getType(func.type());
		blockType = topBlockType;
		locals = new ArrayList<>(args.length + func.locals().size());
		locals.addAll(Arrays.asList(args));
		for(var local : func.locals()) {
			locals.add(Defaults.defaultValue(local));
		}
	}

	public StackFrame(InstantiatedModule module, List<? extends Instr> block, FuncType blockType, Object[] localValues, Object[] initStack) {
		this.module = module;
		this.block = block;
		topBlockType = blockType;
		this.blockType = topBlockType;
		locals = new ArrayList<>(List.of(localValues));
		pushAll(initStack);
	}

	private final InstantiatedModule module;
	private final FuncType topBlockType;
	private List<? extends Instr> block;
	private FuncType blockType;
	private int ip = 0;
	private final List<Object> locals;
	private final ArrayList<Object> stack = new ArrayList<>();
	
	private void push(Object value) {
		stack.add(value);
	}
	
	private Object pop() {
		Object value = stack.get(stack.size() - 1);
		stack.remove(stack.size() - 1);
		return value;
	}

	private Object peek() {
		return stack.get(stack.size() - 1);
	}

	private Object[] getTopValues(int n) {
		Object[] values = new Object[n];
		for(int i = values.length - 1; i >= 0; --i) {
			values[i] = pop();
		}
		return values;
	}

	private void pushAll(Object[] values) {
		for(var value : values) {
			push(value);
		}
	}

	public FunctionResult evaluate() throws Throwable {
		while(true) {
			for(; ip < block.size(); ++ip) {
				FunctionResult result = null;

				switch(block.get(ip)) {
					case NumericInstr numInstr -> evaluateNumInstr(numInstr);
					case VectorInstr vectorInstr -> evaluateVectorInstr(vectorInstr);
					case ReferenceInstr referenceInstr -> evaluateReferenceInstr(referenceInstr);
					case ParametricInstr parametricInstr -> evaluateParametricInstruction(parametricInstr);
					case VariableInstr variableInstr -> evaluateVariableInstruction(variableInstr);
					case TableInstr tableInstr -> evaluateTableInstruction(tableInstr);
					case MemoryInstr memoryInstr -> evaluateMemoryInstruction(memoryInstr);
					case ControlInstr controlInstr -> result = evaluateControlInstruction(controlInstr);
				}

				if(result != null) {
					return result;
				}
			}

			Object[] values = getTopValues(blockType.results().types().size());

			if(stack.isEmpty()) {
				return new FunctionResult.Values(values);
			}

			var label = (Label)pop();
			block = label.block;
			blockType = label.outerBlockType;
			ip = label.endIndex;
			pushAll(values);
		}
	}

	private record Label(List<? extends Instr> block, FuncType outerBlockType, ResultType resultType, int branchIndex, int endIndex) {
		@Override
		public String toString() {
			return "Label[outerBlockType=" + outerBlockType + ", resultType=" + resultType + ", branchIndex=" + branchIndex + ", endIndex=" + endIndex + "]";
		}
	}

	private void evaluateNumInstr(NumericInstr instr) {
		switch(instr) {
			case NumericInstr.I32_Const(var value) -> push(value);
			case NumericInstr.I64_Const(var value) -> push(value);
			case NumericInstr.F32_Const(var value) -> push(value);
			case NumericInstr.F64_Const(var value) -> push(value);

			case NumericInstr.Inn_IUnOp innIUnOp -> {
				switch(innIUnOp.size()) {
					case _32 -> {
						int a = (int)pop();

						int result = switch(innIUnOp.op()) {
							case CLZ -> Integer.numberOfLeadingZeros(a);
							case CTZ -> Integer.numberOfTrailingZeros(a);
							case POPCNT -> Integer.bitCount(a);
						};
						push(result);
					}
					case _64 -> {
						long a = (long)pop();

						long result = switch(innIUnOp.op()) {
							case CLZ -> Long.numberOfLeadingZeros(a);
							case CTZ -> Long.numberOfTrailingZeros(a);
							case POPCNT -> Long.bitCount(a);
						};
						push(result);
					}
				}
			}

			case NumericInstr.Fnn_FUnOp fnnFUnOp -> {
				switch(fnnFUnOp.size()) {
					case _32 -> {
						float a = (float)pop();

						float result = switch(fnnFUnOp.op()) {
							case ABS -> Math.abs(a);
							case NEG -> -a;
							case SQRT -> (float)Math.sqrt(a);
							case CEIL -> Util.ceilF32(a);
							case FLOOR -> Util.floorF32(a);
							case TRUNC -> Util.truncF32(a);
							case NEAREST -> Util.nearestF32(a);
						};
						push(result);
					}
					case _64 -> {
						double a = (double)pop();

						double result = switch(fnnFUnOp.op()) {
							case ABS -> Math.abs(a);
							case NEG -> -a;
							case SQRT -> Math.sqrt(a);
							case CEIL -> Util.ceilF64(a);
							case FLOOR -> Util.floorF64(a);
							case TRUNC -> Util.truncF64(a);
							case NEAREST -> Util.nearestF64(a);
						};
						push(result);
					}
				}
			}

			case NumericInstr.Inn_IBinOp iBinOpInstr -> {
				switch(iBinOpInstr.size()) {
					case _32 -> {
						int b = (int)pop();
						int a = (int)pop();

						int result = switch(iBinOpInstr.op()) {
							case ADD -> a + b;
							case SUB -> a - b;
							case MUL -> a * b;
							case DIV_U -> Integer.divideUnsigned(a, b);
							case DIV_S -> {
								if(a == Integer.MIN_VALUE && b == -1) {
									throw new ArithmeticException();
								}
								yield a / b;
							}
							case REM_U -> Integer.remainderUnsigned(a, b);
							case REM_S -> a % b;
							case AND -> a & b;
							case OR -> a | b;
							case XOR -> a ^ b;
							case SHL -> a << b;
							case SHR_U -> a >>> b;
							case SHR_S -> a >> b;
							case ROTL -> Integer.rotateLeft(a, b);
							case ROTR -> Integer.rotateRight(a, b);
						};
						push(result);
					}
					case _64 -> {
						long b = (long)pop();
						long a = (long)pop();

						long result = switch(iBinOpInstr.op()) {
							case ADD -> a + b;
							case SUB -> a - b;
							case MUL -> a * b;
							case DIV_U -> Long.divideUnsigned(a, b);
							case DIV_S -> {
								if(a == Long.MIN_VALUE && b == -1) {
									throw new ArithmeticException();
								}
								yield a / b;
							}
							case REM_U -> Long.remainderUnsigned(a, b);
							case REM_S -> a % b;
							case AND -> a & b;
							case OR -> a | b;
							case XOR -> a ^ b;
							case SHL -> a << b;
							case SHR_U -> a >>> b;
							case SHR_S -> a >> b;
							case ROTL -> Long.rotateLeft(a, (int)b);
							case ROTR -> Long.rotateRight(a, (int)b);
						};
						push(result);
					}
				}
			}
			
			case NumericInstr.Fnn_FBinOp fnnFBinOp -> {
				switch(fnnFBinOp.size()) {
					case _32 -> {
						float b = (float)pop();
						float a = (float)pop();

						float result = switch(fnnFBinOp.op()) {
							case ADD -> a + b;
							case SUB -> a - b;
							case MUL -> a * b;
							case DIV -> a / b;
							case MIN -> Util.minF32(a, b);
							case MAX -> Util.maxF32(a, b);
							case COPYSIGN -> Math.copySign(a, b);
						};
						push(result);
					}
					case _64 -> {
						double b = (double)pop();
						double a = (double)pop();

						double result = switch(fnnFBinOp.op()) {
							case ADD -> a + b;
							case SUB -> a - b;
							case MUL -> a * b;
							case DIV -> a / b;
							case MIN -> Util.minF64(a, b);
							case MAX -> Util.maxF64(a, b);
							case COPYSIGN -> Math.copySign(a, b);
						};
						push(result);
					}
				}
			}

			case NumericInstr.Inn_ITestOp innITestOp -> {
				switch(innITestOp.size()) {
					case _32 -> {
						int a = (int)pop();

						boolean result = switch(innITestOp.op()) {
							case EQZ -> a == 0;
						};
						push(result ? 1 : 0);
					}
					case _64 -> {
						long a = (long)pop();

						boolean result = switch(innITestOp.op()) {
							case EQZ -> a == 0;
						};
						push(result ? 1 : 0);
					}
				}
			}
			
			case NumericInstr.Inn_IRelOp innIRelOp -> {
				switch(innIRelOp.size()) {
					case _32 -> {
						int b = (int)pop();
						int a = (int)pop();

						boolean result = switch(innIRelOp.op()) {
							case EQ -> a == b;
							case NE -> a != b;
							case LT_U -> Integer.compareUnsigned(a, b) < 0;
							case LT_S -> a < b;
							case GT_U -> Integer.compareUnsigned(a, b) > 0;
							case GT_S -> a > b;
							case LE_U -> Integer.compareUnsigned(a, b) <= 0;
							case LE_S -> a <= b;
							case GE_U -> Integer.compareUnsigned(a, b) >= 0;
							case GE_S -> a >= b;
						};
						push(result ? 1 : 0);
					}
					case _64 -> {
						long b = (long)pop();
						long a = (long)pop();

						boolean result = switch(innIRelOp.op()) {
							case EQ -> a == b;
							case NE -> a != b;
							case LT_U -> Long.compareUnsigned(a, b) < 0;
							case LT_S -> a < b;
							case GT_U -> Long.compareUnsigned(a, b) > 0;
							case GT_S -> a > b;
							case LE_U -> Long.compareUnsigned(a, b) <= 0;
							case LE_S -> a <= b;
							case GE_U -> Long.compareUnsigned(a, b) >= 0;
							case GE_S -> a >= b;
						};
						push(result ? 1 : 0);
					}
				}
			}

			case NumericInstr.Fnn_FRelOp fnnFRelOp -> {
				switch(fnnFRelOp.size()) {
					case _32 -> {
						float b = (float)pop();
						float a = (float)pop();

						boolean result = switch(fnnFRelOp.op()) {
							case EQ -> a == b;
							case NE -> a != b;
							case LT -> a < b;
							case GT -> a > b;
							case LE -> a <= b;
							case GE -> a >= b;
						};
						push(result ? 1 : 0);
					}
					case _64 -> {
						double b = (double)pop();
						double a = (double)pop();

						boolean result = switch(fnnFRelOp.op()) {
							case EQ -> a == b;
							case NE -> a != b;
							case LT -> a < b;
							case GT -> a > b;
							case LE -> a <= b;
							case GE -> a >= b;
						};
						push(result ? 1 : 0);
					}
				}
			}

			case NumericInstr.Inn_Extend8_S innExtend8S -> {
				switch(innExtend8S.size()) {
					case _32 -> {
						int a = (int)pop();
						push((int)(byte)a);
					}
					case _64 -> {
						long a = (long)pop();
						push((long)(byte)a);
					}
				}
			}

			case NumericInstr.Inn_Extend16_S innExtend16S -> {
				switch(innExtend16S.size()) {
					case _32 -> {
						int a = (int)pop();
						int result = (short)a;
						push((int)(short)a);
					}
					case _64 -> {
						long a = (long)pop();
						push((long)(short)a);
					}
				}
			}

			case NumericInstr.I64_Extend32_S() -> {
				long a = (long)pop();
				push((long)(int)a);
			}

			case NumericInstr.I32_Wrap_I64() -> {
				long a = (long)pop();
				push((int)a);
			}

			case NumericInstr.I64_Extend_I32_S() -> {
				int a = (int)pop();
				push((long)a);
			}

			case NumericInstr.I64_Extend_I32_U() -> {
				int a = (int)pop();
				push(Integer.toUnsignedLong(a));
			}

			case NumericInstr.Inn_Trunc_Fmm_S innTruncFmmS -> {
				BigDecimal value = switch(innTruncFmmS.floatSize()) {
					case _32 -> {
						float a = (float)pop();
						if(!Float.isFinite(a)) {
							throw new ArithmeticException();
						}

						yield new BigDecimal(a);
					}
					case _64 -> {
						double a = (double)pop();
						if(!Double.isFinite(a)) {
							throw new ArithmeticException();
						}

						yield new BigDecimal(a);
					}
				};

				switch(innTruncFmmS.intSize()) {
					case _32 -> {
						if(value.compareTo(new BigDecimal((long)Integer.MIN_VALUE - 1)) <= 0 || value.compareTo(new BigDecimal((long)Integer.MAX_VALUE + 1)) >= 0) {
							throw new ArithmeticException();
						}
						push(value.intValue());
					}
					case _64 -> {
						if(value.compareTo(new BigDecimal(Long.MIN_VALUE).subtract(BigDecimal.ONE)) <= 0 || value.compareTo(new BigDecimal(Long.MAX_VALUE).add(BigDecimal.ONE)) >= 0) {
							throw new ArithmeticException();
						}
						push(value.longValue());
					}
				}
			}
			case NumericInstr.Inn_Trunc_Fmm_U innTruncFmmU -> {
				BigDecimal value = switch(innTruncFmmU.floatSize()) {
					case _32 -> {
						float a = (float)pop();
						if(!Float.isFinite(a) || a <= -1.0f) {
							throw new ArithmeticException();
						}

						yield new BigDecimal(a);
					}
					case _64 -> {
						double a = (double)pop();
						if(!Double.isFinite(a) || a <= -1.0) {
							throw new ArithmeticException();
						}

						yield new BigDecimal(a);
					}
				};


				switch(innTruncFmmU.intSize()) {
					case _32 -> {
						if(value.compareTo(new BigDecimal(4294967296L)) >= 0) {
							throw new ArithmeticException();
						}
						push(value.intValue());
					}
					case _64 -> {
						if(value.compareTo(new BigDecimal("18446744073709551616")) >= 0) {
							throw new ArithmeticException();
						}
						push(value.longValue());
					}
				}
			}
			case NumericInstr.Inn_Trunc_Sat_Fmm_S innTruncSatFmmS -> {
				switch(innTruncSatFmmS.floatSize()) {
					case _32 -> {
						float a = (float)pop();

						switch(innTruncSatFmmS.intSize()) {
							case _32 -> push((int)a);
							case _64 -> push((long)a);
						}
					}
					case _64 -> {
						double a = (double)pop();

						switch(innTruncSatFmmS.intSize()) {
							case _32 -> push((int)a);
							case _64 -> push((long)a);
						}
					}
				}
			}

			case NumericInstr.Inn_Trunc_Sat_Fmm_U innTruncSatFmmU -> {
				switch(innTruncSatFmmU.floatSize()) {
					case _32 -> {
						float a = (float)pop();

						switch(innTruncSatFmmU.intSize()) {
							case _32 -> push(Util.truncSatF32U32(a));
							case _64 -> push(Util.truncSatF32U64(a));
						}
					}
					case _64 -> {
						double a = (double)pop();

						switch(innTruncSatFmmU.intSize()) {
							case _32 -> push(Util.truncSatF64U32(a));
							case _64 -> push(Util.truncSatF64U64(a));
						}
					}
				}
			}

			case NumericInstr.F32_Demote_F64() -> {
				double a = (double)pop();
				push((float)a);
			}
			case NumericInstr.F64_Promote_F32() -> {
				float a = (float)pop();
				push((double)a);
			}

			case NumericInstr.Fnn_Convert_Imm_S fnnConvertImmS -> {
				switch(fnnConvertImmS.floatSize()) {
					case _32 -> {
						float result = switch(fnnConvertImmS.intSize()) {
							case _32 -> (float)(int)pop();
							case _64 -> (float)(long)pop();
						};
						push(result);
					}
					case _64 -> {
						double result = switch(fnnConvertImmS.intSize()) {
							case _32 -> (double)(int)pop();
							case _64 -> (double)(long)pop();
						};
						push(result);
					}
				}
			}

			case NumericInstr.Fnn_Convert_Imm_U fnnConvertImmU -> {
				switch(fnnConvertImmU.floatSize()) {
					case _32 -> {
						float result = switch(fnnConvertImmU.intSize()) {
							case _32 -> (float)Integer.toUnsignedLong((int)pop());
							case _64 -> new BigDecimal(Long.toUnsignedString((long)pop())).floatValue();
						};
						push(result);
					}
					case _64 -> {
						double result = switch(fnnConvertImmU.intSize()) {
							case _32 -> (double)Integer.toUnsignedLong((int)pop());
							case _64 -> new BigDecimal(Long.toUnsignedString((long)pop())).doubleValue();
						};
						push(result);
					}
				}
			}

			case NumericInstr.Fnn_Reinterpret_Inn fnnReinterpretInn -> {
				switch(fnnReinterpretInn.size()) {
					case _32 -> {
						int a = (int)pop();
						float result = Float.intBitsToFloat(a);
						push(result);
					}
					case _64 -> {
						long a = (long)pop();
						double result = Double.longBitsToDouble(a);
						push(result);
					}
				}
			}

			case NumericInstr.Inn_Reinterpret_Fnn innReinterpretFnn -> {
				switch(innReinterpretFnn.size()) {
					case _32 -> {
						float a = (float)pop();
						int result = Float.floatToRawIntBits(a);
						push(result);
					}
					case _64 -> {
						double a = (double)pop();
						long result = Double.doubleToRawLongBits(a);
						push(result);
					}
				}
			}
		}
	}

	private void evaluateVectorInstr(VectorInstr instr) throws Throwable {
		switch(instr) {
			case VectorInstr.V128_Const(var value) -> push(value);

			case VectorInstr.VVUnOp vvUnOp -> {
				V128 a = (V128)pop();

				V128.Unary8Function f = switch(vvUnOp) {
					case NOT -> b -> (byte)~b;

				};


				V128 result = a.unary8(f);

				push(result);
			}

			case VectorInstr.VVBinOp vvBinOp -> {
				V128 b = (V128)pop();
				V128 a = (V128)pop();

				V128.Binary8Function f = switch(vvBinOp) {
					case AND -> (b0, b1) -> (byte)(b0 & b1);
					case ANDNOT -> (b0, b1) -> (byte)(b0 & ~b1);
					case OR -> (b0, b1) -> (byte)(b0 | b1);
					case XOR -> (b0, b1) -> (byte)(b0 ^ b1);
				};

				V128 result = a.binary8(b, f);
				push(result);
			}

			case VectorInstr.VVTernOp vvTernOp -> {
				V128 c = (V128)pop();
				V128 b = (V128)pop();
				V128 a = (V128)pop();

				V128.Ternary8Function f = switch(vvTernOp) {
					case BITSELECT -> (b0, b1, b2) -> (byte)((b0 & b2) | (b1 & ~b2));
				};

				V128 result = a.ternary8(b, c, f);
				push(result);
			}

			case VectorInstr.VVTestOp vvTestOp -> {
				V128 a = (V128)pop();

				int result = switch(vvTestOp) {
					case ANY_TRUE -> a.anyTrue() ? 1 : 0;
				};

				push(result);
			}

			case VectorInstr.I8x16_Op_Instr(var op) -> {
				switch(op) {
					case VectorInstr.Swizzle() -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();
						push(a.swizzle8(b));
					}

					case VectorInstr.Shuffle(var laneIndexes) -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();
						push(laneIndexes.shuffle8(a, b));
					}

					case VectorInstr.Splat() -> {
						int a = (int)pop();
						push(V128.splat8((byte)a));
					}

					case VectorInstr.ExtractLane_U(var laneIdx) -> {
						V128 a = (V128)pop();
						byte result = a.extractLane8(laneIdx);
						push(Byte.toUnsignedInt(result));
					}

					case VectorInstr.ExtractLane_S(var laneIdx) -> {
						V128 a = (V128)pop();
						byte result = a.extractLane8(laneIdx);
						push((int)result);
					}

					case VectorInstr.ReplaceLane(var laneIdx) -> {
						int b = (int)pop();
						V128 a = (V128)pop();
						V128 result = a.replaceLane8(laneIdx, (byte)b);
						push(result);
					}

					case VectorInstr.VIRelOp viRelOp -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128.Binary8Function f = switch(viRelOp) {
							case VectorInstr.VIRelOp_S viRelOpS -> switch(viRelOpS) {
								case EQ -> (n0, n1) -> n0 == n1 ? (byte)-1 : (byte)0;
								case NE -> (n0, n1) -> n0 != n1 ? (byte)-1 : (byte)0;
								case LT_S -> (n0, n1) -> n0 < n1 ? (byte)-1 : (byte)0;
								case GT_S -> (n0, n1) -> n0 > n1 ? (byte)-1 : (byte)0;
								case LE_S -> (n0, n1) -> n0 <= n1 ? (byte)-1 : (byte)0;
								case GE_S -> (n0, n1) -> n0 >= n1 ? (byte)-1 : (byte)0;
							};
							case VectorInstr.VIRelOp_U viRelOpU -> switch(viRelOpU) {
								case LT_U -> (n0, n1) -> Byte.compareUnsigned(n0, n1) < 0 ? (byte)-1 : (byte)0;
								case GT_U -> (n0, n1) -> Byte.compareUnsigned(n0, n1) > 0 ? (byte)-1 : (byte)0;
								case LE_U -> (n0, n1) -> Byte.compareUnsigned(n0, n1) <= 0 ? (byte)-1 : (byte)0;
								case GE_U -> (n0, n1) -> Byte.compareUnsigned(n0, n1) >= 0 ? (byte)-1 : (byte)0;
							};
						};

						push(a.binary8(b, f));
					}

					case VectorInstr.VIUnOp viUnOp -> {
						V128 a = (V128)pop();

						V128.Unary8Function f = switch(viUnOp) {
							case ABS -> n0 -> (byte)Math.abs(n0);
							case NEG -> n0 -> (byte)-n0;
						};

						push(a.unary8(f));
					}

					case VectorInstr.Popcnt() -> {
						V128 a = (V128)pop();
						push(a.unary8(n0 -> (byte)Integer.bitCount(Byte.toUnsignedInt(n0))));
					}

					case VectorInstr.All_True() -> {
						V128 a = (V128)pop();
						boolean result = a.allTrue8();
						push(result ? 1 : 0);
					}

					case VectorInstr.BitMask() -> {
						V128 a = (V128)pop();
						int result = a.bitmask8();
						push(result);
					}

					case VectorInstr.I8x16_Narrow_I16x8_U() -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128 result = V128.build8(i -> Util.narrowU16I8(i < 8 ? a.extractLane16(i) : b.extractLane16(i - 8)));
						push(result);
					}

					case VectorInstr.I8x16_Narrow_I16x8_S() -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128 result = V128.build8(i -> Util.narrowS16I8(i < 8 ? a.extractLane16(i) : b.extractLane16(i - 8)));
						push(result);
					}

					case VectorInstr.VIShiftOp viShiftOp -> {
						int b = (int)pop() & 0x07;
						V128 a = (V128)pop();

						V128.Unary8Function f = switch(viShiftOp) {
							case SHL -> n0 -> (byte)(n0 << b);
							case SHR_U -> n0 -> (byte)(Byte.toUnsignedInt(n0) >>> b);
							case SHR_S -> n0 -> (byte)(n0 >> b);
						};

						push(a.unary8(f));
					}

					case VectorInstr.VIBinOp viBinOp -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128.Binary8Function f = switch(viBinOp) {
							case ADD -> (n0, n1) -> (byte)(n0 + n1);
							case SUB -> (n0, n1) -> (byte)(n0 - n1);
						};

						push(a.binary8(b, f));
					}

					case VectorInstr.VIMinMaxOp viBinOp -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128.Binary8Function f = switch(viBinOp) {
							case MIN_U -> (n0, n1) -> (byte)Math.min(Byte.toUnsignedInt(n0), Byte.toUnsignedInt(n1));
							case MIN_S -> (n0, n1) -> (byte)Math.min(n0, n1);
							case MAX_U -> (n0, n1) -> (byte)Math.max(Byte.toUnsignedInt(n0), Byte.toUnsignedInt(n1));
							case MAX_S -> (n0, n1) -> (byte)Math.max(n0, n1);
						};

						push(a.binary8(b, f));
					}

					case VectorInstr.VISatBinOp viSatBinOp -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128.Binary8Function f = switch(viSatBinOp) {
							case ADD_SAT_U -> Util::addSatU8;
							case ADD_SAT_S -> Util::addSatS8;
							case SUB_SAT_U -> Util::subSatU8;
							case SUB_SAT_S -> Util::subSatS8;
						};

						push(a.binary8(b, f));
					}

					case VectorInstr.VIAverageOps viAverageOps -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128.Binary8Function f = switch(viAverageOps) {
							case AVGR_U -> (n0, n1) -> (byte)((Byte.toUnsignedInt(n0) + Byte.toUnsignedInt(n1) + 1) / 2);
						};

						push(a.binary8(b, f));
					}
				}
			}
			case VectorInstr.I16x8_Op_Instr(var op) -> {
				switch(op) {
					case VectorInstr.Splat() -> {
						int a = (int)pop();
						push(V128.splat16((short)a));
					}

					case VectorInstr.ExtractLane_U(var laneIdx) -> {
						V128 a = (V128)pop();
						short result = a.extractLane16(laneIdx);
						push(Short.toUnsignedInt(result));
					}

					case VectorInstr.ExtractLane_S(var laneIdx) -> {
						V128 a = (V128)pop();
						short result = a.extractLane16(laneIdx);
						push((int)result);
					}

					case VectorInstr.ReplaceLane(var laneIdx) -> {
						int b = (int)pop();
						V128 a = (V128)pop();
						V128 result = a.replaceLane16(laneIdx, (short)b);
						push(result);
					}

					case VectorInstr.VIRelOp viRelOp -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128.Binary16Function f = switch(viRelOp) {
							case VectorInstr.VIRelOp_S viRelOpS -> switch(viRelOpS) {
								case EQ -> (n0, n1) -> n0 == n1 ? (short)-1 : (short)0;
								case NE -> (n0, n1) -> n0 != n1 ? (short)-1 : (short)0;
								case LT_S -> (n0, n1) -> n0 < n1 ? (short)-1 : (short)0;
								case GT_S -> (n0, n1) -> n0 > n1 ? (short)-1 : (short)0;
								case LE_S -> (n0, n1) -> n0 <= n1 ? (short)-1 : (short)0;
								case GE_S -> (n0, n1) -> n0 >= n1 ? (short)-1 : (short)0;
							};
							case VectorInstr.VIRelOp_U viRelOpU -> switch(viRelOpU) {
								case LT_U -> (n0, n1) -> Short.compareUnsigned(n0, n1) < 0 ? (short)-1 : (short)0;
								case GT_U -> (n0, n1) -> Short.compareUnsigned(n0, n1) > 0 ? (short)-1 : (short)0;
								case LE_U -> (n0, n1) -> Short.compareUnsigned(n0, n1) <= 0 ? (short)-1 : (short)0;
								case GE_U -> (n0, n1) -> Short.compareUnsigned(n0, n1) >= 0 ? (short)-1 : (short)0;
							};
						};

						push(a.binary16(b, f));
					}

					case VectorInstr.VIUnOp viUnOp -> {
						V128 a = (V128)pop();

						V128.Unary16Function f = switch(viUnOp) {
							case ABS -> n0 -> (short)Math.abs(n0);
							case NEG -> n0 -> (short)-n0;
						};

						push(a.unary16(f));
					}

					case VectorInstr.Q15mulr_Sat_S() -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128 result = a.binary16(b, (n0, n1) -> Util.narrowS32I16((n0 * n1 + (1 << 14)) >> 15));

						push(result);
					}

					case VectorInstr.All_True() -> {
						V128 a = (V128)pop();
						boolean result = a.allTrue16();
						push(result ? 1 : 0);
					}

					case VectorInstr.BitMask() -> {
						V128 a = (V128)pop();
						int result = a.bitmask16();
						push(result);
					}

					case VectorInstr.I16x8_Narrow_I32x4_U() -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128 result = V128.build16(i -> Util.narrowU32I16(i < 4 ? a.extractLane32(i) : b.extractLane32(i - 4)));
						push(result);
					}

					case VectorInstr.I16x8_Narrow_I32x4_S() -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128 result = V128.build16(i -> Util.narrowS32I16(i < 4 ? a.extractLane32(i) : b.extractLane32(i - 4)));
						push(result);
					}

					case VectorInstr.I16x8_Extend_Low_I8x16_U() -> {
						V128 a = (V128)pop();
						V128 result = V128.build16(i -> (short)Byte.toUnsignedInt(a.extractLane8(i)));
						push(result);
					}

					case VectorInstr.I16x8_Extend_Low_I8x16_S() -> {
						V128 a = (V128)pop();
						V128 result = V128.build16(i -> (short)a.extractLane8(i));
						push(result);
					}

					case VectorInstr.I16x8_Extend_High_I8x16_U() -> {
						V128 a = (V128)pop();
						V128 result = V128.build16(i -> (short)Byte.toUnsignedInt(a.extractLane8(i + 8)));
						push(result);
					}

					case VectorInstr.I16x8_Extend_High_I8x16_S() -> {
						V128 a = (V128)pop();
						V128 result = V128.build16(i -> (short)a.extractLane8(i + 8));
						push(result);
					}

					case VectorInstr.VIShiftOp viShiftOp -> {
						int b = (int)pop() & 0x0F;
						V128 a = (V128)pop();

						V128.Unary16Function f = switch(viShiftOp) {
							case SHL -> n0 -> (short)(n0 << b);
							case SHR_U -> n0 -> (short)(Short.toUnsignedInt(n0) >>> b);
							case SHR_S -> n0 -> (short)(n0 >> b);
						};

						push(a.unary16(f));
					}

					case VectorInstr.VIBinOp viBinOp -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128.Binary16Function f = switch(viBinOp) {
							case ADD -> (n0, n1) -> (short)(n0 + n1);
							case SUB -> (n0, n1) -> (short)(n0 - n1);
						};

						push(a.binary16(b, f));
					}

					case VectorInstr.VIMinMaxOp viBinOp -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128.Binary16Function f = switch(viBinOp) {
							case MIN_U -> (n0, n1) -> (short)Math.min(Short.toUnsignedInt(n0), Short.toUnsignedInt(n1));
							case MIN_S -> (n0, n1) -> (short)Math.min(n0, n1);
							case MAX_U -> (n0, n1) -> (short)Math.max(Short.toUnsignedInt(n0), Short.toUnsignedInt(n1));
							case MAX_S -> (n0, n1) -> (short)Math.max(n0, n1);
						};

						push(a.binary16(b, f));
					}

					case VectorInstr.VISatBinOp viSatBinOp -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128.Binary16Function f = switch(viSatBinOp) {
							case ADD_SAT_U -> Util::addSatU16;
							case ADD_SAT_S -> Util::addSatS16;
							case SUB_SAT_U -> Util::subSatU16;
							case SUB_SAT_S -> Util::subSatS16;
						};

						push(a.binary16(b, f));
					}

					case VectorInstr.VIMulOp viMulOp -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128.Binary16Function f = switch(viMulOp) {
							case MUL -> (n0, n1) -> (short)(n0 * n1);
						};

						push(a.binary16(b, f));
					}

					case VectorInstr.I16x8_ExtMul_Low_I8x16_U() -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128 result = V128.build16(i -> (short)(Byte.toUnsignedInt(a.extractLane8(i)) * Byte.toUnsignedInt(b.extractLane8(i))));

						push(result);
					}

					case VectorInstr.I16x8_ExtMul_Low_I8x16_S() -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128 result = V128.build16(i -> (short)(a.extractLane8(i) * b.extractLane8(i)));

						push(result);
					}

					case VectorInstr.I16x8_ExtMul_High_I8x16_U() -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128 result = V128.build16(i -> (short)(Byte.toUnsignedInt(a.extractLane8(i + 8)) * Byte.toUnsignedInt(b.extractLane8(i + 8))));

						push(result);
					}

					case VectorInstr.I16x8_ExtMul_High_I8x16_S() -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128 result = V128.build16(i -> (short)(a.extractLane8(i + 8) * b.extractLane8(i + 8)));

						push(result);
					}

					case VectorInstr.VIAverageOps viAverageOps -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128.Binary16Function f = switch(viAverageOps) {
							case AVGR_U -> (n0, n1) -> (short)((Short.toUnsignedInt(n0) + Short.toUnsignedInt(n1) + 1) / 2);
						};

						push(a.binary16(b, f));
					}

					case VectorInstr.I16x8_ExtAdd_Pairwise_I8x16_U() -> {
						V128 a = (V128)pop();

						V128 result = V128.build16(i -> (short)(Byte.toUnsignedInt(a.extractLane8(i)) + Byte.toUnsignedInt(a.extractLane8(i + 8))));

						push(result);
					}

					case VectorInstr.I16x8_ExtAdd_Pairwise_I8x16_S() -> {
						V128 a = (V128)pop();

						V128 result = V128.build16(i -> (short)(a.extractLane8(i) + a.extractLane8(i + 8)));

						push(result);
					}
				}
			}

			case VectorInstr.I32x4_Op_Instr(var op) -> {
				switch(op) {
					case VectorInstr.Splat() -> {
						int a = (int)pop();
						push(V128.splat32(a));
					}

					case VectorInstr.ExtractLane(var laneIdx) -> {
						V128 a = (V128)pop();
						int result = a.extractLane32(laneIdx);
						push(result);
					}

					case VectorInstr.ReplaceLane(var laneIdx) -> {
						int b = (int)pop();
						V128 a = (V128)pop();
						V128 result = a.replaceLane32(laneIdx, b);
						push(result);
					}

					case VectorInstr.VIRelOp viRelOp -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128.Binary32Function f = switch(viRelOp) {
							case VectorInstr.VIRelOp_S viRelOpS -> switch(viRelOpS) {
								case EQ -> (n0, n1) -> n0 == n1 ? -1 : 0;
								case NE -> (n0, n1) -> n0 != n1 ? -1 : 0;
								case LT_S -> (n0, n1) -> n0 < n1 ? -1 : 0;
								case GT_S -> (n0, n1) -> n0 > n1 ? -1 : 0;
								case LE_S -> (n0, n1) -> n0 <= n1 ? -1 : 0;
								case GE_S -> (n0, n1) -> n0 >= n1 ? -1 : 0;
							};
							case VectorInstr.VIRelOp_U viRelOpU -> switch(viRelOpU) {
								case LT_U -> (n0, n1) -> Integer.compareUnsigned(n0, n1) < 0 ? -1 : 0;
								case GT_U -> (n0, n1) -> Integer.compareUnsigned(n0, n1) > 0 ? -1 : 0;
								case LE_U -> (n0, n1) -> Integer.compareUnsigned(n0, n1) <= 0 ? -1 : 0;
								case GE_U -> (n0, n1) -> Integer.compareUnsigned(n0, n1) >= 0 ? -1 : 0;
							};
						};

						push(a.binary32(b, f));
					}

					case VectorInstr.VIUnOp viUnOp -> {
						V128 a = (V128)pop();

						V128.Unary32Function f = switch(viUnOp) {
							case ABS -> Math::abs;
							case NEG -> n0 -> -n0;
						};

						push(a.unary32(f));
					}

					case VectorInstr.Dot_I16x8_S() -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();
						V128 result = V128.build32(i -> a.extractLane16(i) * b.extractLane16(i) + a.extractLane16(i + 4) * b.extractLane16(i + 4));
						push(result);
					}

					case VectorInstr.All_True() -> {
						V128 a = (V128)pop();
						boolean result = a.allTrue32();
						push(result ? 1 : 0);
					}

					case VectorInstr.BitMask() -> {
						V128 a = (V128)pop();
						int result = a.bitmask32();
						push(result);
					}

					case VectorInstr.I32x4_Extend_Low_I16x8_U() -> {
						V128 a = (V128)pop();
						V128 result = V128.build32(i -> Short.toUnsignedInt(a.extractLane16(i)));
						push(result);
					}

					case VectorInstr.I32x4_Extend_Low_I16x8_S() -> {
						V128 a = (V128)pop();
						V128 result = V128.build32(a::extractLane16);
						push(result);
					}

					case VectorInstr.I32x4_Extend_High_I16x8_U() -> {
						V128 a = (V128)pop();
						V128 result = V128.build32(i -> Short.toUnsignedInt(a.extractLane16(i + 4)));
						push(result);
					}

					case VectorInstr.I32x4_Extend_High_I16x8_S() -> {
						V128 a = (V128)pop();
						V128 result = V128.build32(i -> a.extractLane16(i + 4));
						push(result);
					}

					case VectorInstr.VIShiftOp viShiftOp -> {
						int b = (int)pop();
						V128 a = (V128)pop();

						V128.Unary32Function f = switch(viShiftOp) {
							case SHL -> n0 -> n0 << b;
							case SHR_U -> n0 -> n0 >>> b;
							case SHR_S -> n0 -> n0 >> b;
						};

						push(a.unary32(f));
					}

					case VectorInstr.VIBinOp viBinOp -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128.Binary32Function f = switch(viBinOp) {
							case ADD -> Integer::sum;
							case SUB -> (n0, n1) -> n0 - n1;
						};

						push(a.binary32(b, f));
					}

					case VectorInstr.VIMinMaxOp viBinOp -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128.Binary32Function f = switch(viBinOp) {
							case MIN_U -> Util::minU32;
							case MIN_S -> Math::min;
							case MAX_U -> Util::maxU32;
							case MAX_S -> Math::max;
						};

						push(a.binary32(b, f));
					}

					case VectorInstr.VIMulOp viMulOp -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128.Binary32Function f = switch(viMulOp) {
							case MUL -> (n0, n1) -> n0 * n1;
						};

						push(a.binary32(b, f));
					}

					case VectorInstr.I32x4_ExtMul_Low_I16x8_U() -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();
						V128 result = V128.build32(i -> Short.toUnsignedInt(a.extractLane16(i)) * Short.toUnsignedInt(b.extractLane16(i)));
						push(result);
					}

					case VectorInstr.I32x4_ExtMul_Low_I16x8_S() -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();
						V128 result = V128.build32(i -> a.extractLane16(i) * b.extractLane16(i));
						push(result);
					}

					case VectorInstr.I32x4_ExtMul_High_I16x8_U() -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();
						V128 result = V128.build32(i -> Short.toUnsignedInt(a.extractLane16(i + 4)) * Short.toUnsignedInt(b.extractLane16(i + 4)));
						push(result);
					}

					case VectorInstr.I32x4_ExtMul_High_I16x8_S() -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();
						V128 result = V128.build32(i -> a.extractLane16(i + 4) * b.extractLane16(i + 4));
						push(result);
					}

					case VectorInstr.I32x4_ExtAdd_Pairwise_I16x8_U() -> {
						V128 a = (V128)pop();
						V128 result = V128.build32(i -> Short.toUnsignedInt(a.extractLane16(i)) + Short.toUnsignedInt(a.extractLane16(i + 4)));
						push(result);
					}

					case VectorInstr.I32x4_ExtAdd_Pairwise_I16x8_S() -> {
						V128 a = (V128)pop();
						V128 result = V128.build32(i -> a.extractLane16(i) + a.extractLane16(i + 4));
						push(result);
					}

					case VectorInstr.I32x4_Trunc_Sat_F32x4_U() -> {
						V128 a = (V128)pop();
						V128 result = V128.build32(i -> Util.truncSatF32U32(a.extractLaneF32(i)));
						push(result);
					}

					case VectorInstr.I32x4_Trunc_Sat_F32x4_S() -> {
						V128 a = (V128)pop();
						V128 result = V128.build32(i -> (int)a.extractLaneF32(i));
						push(result);
					}

					case VectorInstr.I32x4_Trunc_Sat_F64x4_U_Zero() -> {
						V128 a = (V128)pop();
						V128 result = V128.build32(i -> i < 2 ? Util.truncSatF64U32(a.extractLaneF64(i)) : 0);
						push(result);
					}
					case VectorInstr.I32x4_Trunc_Sat_F64x4_S_Zero() -> {
						V128 a = (V128)pop();
						V128 result = V128.build32(i -> i < 2 ? (int)a.extractLaneF64(i) : 0);
						push(result);
					}
				}
			}

			case VectorInstr.I64x2_Op_Instr(var op) -> {
				switch(op) {
					case VectorInstr.Splat() -> {
						long a = (long)pop();
						push(V128.splat64(a));
					}

					case VectorInstr.ExtractLane(var laneIdx) -> {
						V128 a = (V128)pop();
						long result = a.extractLane64(laneIdx);
						push(result);
					}

					case VectorInstr.ReplaceLane(var laneIdx) -> {
						long b = (long)pop();
						V128 a = (V128)pop();
						V128 result = a.replaceLane64(laneIdx, b);
						push(result);
					}

					case VectorInstr.VIRelOp_S viRelOpS -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128.Binary64Function f = switch(viRelOpS) {
							case EQ -> (n0, n1) -> n0 == n1 ? -1 : 0;
							case NE -> (n0, n1) -> n0 != n1 ? -1 : 0;
							case LT_S -> (n0, n1) -> n0 < n1 ? -1 : 0;
							case GT_S -> (n0, n1) -> n0 > n1 ? -1 : 0;
							case LE_S -> (n0, n1) -> n0 <= n1 ? -1 : 0;
							case GE_S -> (n0, n1) -> n0 >= n1 ? -1 : 0;
						};

						push(a.binary64(b, f));
					}

					case VectorInstr.VIUnOp viUnOp -> {
						V128 a = (V128)pop();

						V128.Unary64Function f = switch(viUnOp) {
							case ABS -> Math::abs;
							case NEG -> n0 -> -n0;
						};

						push(a.unary64(f));
					}

					case VectorInstr.All_True() -> {
						V128 a = (V128)pop();
						boolean result = a.allTrue64();
						push(result ? 1 : 0);
					}

					case VectorInstr.BitMask() -> {
						V128 a = (V128)pop();
						int result = a.bitmask64();
						push(result);
					}

					case VectorInstr.I64x2_Extend_Low_I32x4_U() -> {
						V128 a = (V128)pop();
						V128 result = V128.build64(i -> Integer.toUnsignedLong(a.extractLane32(i)));
						push(result);
					}

					case VectorInstr.I64x2_Extend_Low_I32x4_S() -> {
						V128 a = (V128)pop();
						V128 result = V128.build64(i -> (long)a.extractLane32(i));
						push(result);
					}

					case VectorInstr.I64x2_Extend_High_I32x4_U() -> {
						V128 a = (V128)pop();
						V128 result = V128.build64(i -> Integer.toUnsignedLong(a.extractLane32(i + 2)));
						push(result);
					}

					case VectorInstr.I64x2_Extend_High_I32x4_S() -> {
						V128 a = (V128)pop();
						V128 result = V128.build64(i -> (long)a.extractLane32(i + 2));
						push(result);
					}

					case VectorInstr.VIShiftOp viShiftOp -> {
						int b = (int)pop();
						V128 a = (V128)pop();

						V128.Unary64Function f = switch(viShiftOp) {
							case SHL -> n0 -> n0 << b;
							case SHR_U -> n0 -> n0 >>> b;
							case SHR_S -> n0 -> n0 >> b;
						};

						push(a.unary64(f));
					}

					case VectorInstr.VIBinOp viBinOp -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128.Binary64Function f = switch(viBinOp) {
							case ADD -> Long::sum;
							case SUB -> (n0, n1) -> n0 - n1;
						};

						push(a.binary64(b, f));
					}

					case VectorInstr.VIMulOp viMulOp -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128.Binary64Function f = switch(viMulOp) {
							case MUL -> (n0, n1) -> n0 * n1;
						};

						push(a.binary64(b, f));
					}

					case VectorInstr.I64x2_ExtMul_Low_I32x4_U() -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128 result = V128.build64(i -> Integer.toUnsignedLong(a.extractLane32(i)) * Integer.toUnsignedLong(b.extractLane32(i)));

						push(result);
					}

					case VectorInstr.I64x2_ExtMul_Low_I32x4_S() -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128 result = V128.build64(i -> (long)a.extractLane32(i) * (long)b.extractLane32(i));

						push(result);
					}

					case VectorInstr.I64x2_ExtMul_High_I32x4_U() -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128 result = V128.build64(i -> Integer.toUnsignedLong(a.extractLane32(i + 2)) * Integer.toUnsignedLong(b.extractLane32(i + 2)));

						push(result);
					}

					case VectorInstr.I64x2_ExtMul_High_I32x4_S() -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128 result = V128.build64(i -> (long)a.extractLane32(i + 2) * (long)b.extractLane32(i + 2));

						push(result);
					}
				}
			}

			case VectorInstr.F32x4_Op_Instr(var op) -> {
				switch(op) {
					case VectorInstr.Splat() -> {
						float a = (float)pop();
						push(V128.splatF32(a));
					}

					case VectorInstr.ExtractLane(var laneIdx) -> {
						V128 a = (V128)pop();
						float result = a.extractLaneF32(laneIdx);
						push(result);
					}

					case VectorInstr.ReplaceLane(var laneIdx) -> {
						float b = (float)pop();
						V128 a = (V128)pop();
						V128 result = a.replaceLaneF32(laneIdx, b);
						push(result);
					}

					case VectorInstr.VFRelOp vfRelOp -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128 result = V128.build32(switch(vfRelOp) {
							case EQ -> i -> a.extractLaneF32(i) == b.extractLaneF32(i) ? -1 : 0;
							case NE -> i -> a.extractLaneF32(i) != b.extractLaneF32(i) ? -1 : 0;
							case LT -> i -> a.extractLaneF32(i) < b.extractLaneF32(i) ? -1 : 0;
							case GT -> i -> a.extractLaneF32(i) > b.extractLaneF32(i) ? -1 : 0;
							case LE -> i -> a.extractLaneF32(i) <= b.extractLaneF32(i) ? -1 : 0;
							case GE -> i -> a.extractLaneF32(i) >= b.extractLaneF32(i) ? -1 : 0;
						});

						push(result);
					}

					case VectorInstr.VFUnOp vfUnOp -> {
						V128 a = (V128)pop();

						V128.UnaryF32Function f = switch(vfUnOp) {
							case ABS -> Math::abs;
							case NEG -> n0 -> -n0;
							case SQRT -> n0 -> (float)Math.sqrt(n0);
							case CEIL -> Util::ceilF32;
							case FLOOR -> Util::floorF32;
							case TRUNC -> Util::truncF32;
							case NEAREST -> Util::nearestF32;
						};

						push(a.unaryF32(f));
					}

					case VectorInstr.VFBinOp vfBinOp -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128.BinaryF32Function f = switch(vfBinOp) {
							case ADD -> Float::sum;
							case SUB -> (n0, n1) -> n0 - n1;
							case MUL -> (n0, n1) -> n0 * n1;
							case DIV -> (n0, n1) -> n0 / n1;
							case MIN -> Util::minF32;
							case MAX -> Util::maxF32;
							case PMIN -> (n0, n1) -> n1 < n0 ? n1 : n0;
							case PMAX -> (n0, n1) -> n0 < n1 ? n1 : n0;
						};

						push(a.binaryF32(b, f));
					}

					case VectorInstr.F32x4_Convert_I32x4_U() -> {
						V128 a = (V128)pop();
						V128 result = V128.buildF32(i -> (float)Integer.toUnsignedLong(a.extractLane32(i)));
						push(result);
					}

					case VectorInstr.F32x4_Convert_I32x4_S() -> {
						V128 a = (V128)pop();
						V128 result = V128.buildF32(i -> (float)a.extractLane32(i));
						push(result);
					}

					case VectorInstr.F32x4_Demote_F64x2_Zero() -> {
						V128 a = (V128)pop();
						V128 result = V128.buildF32(i -> i < 2 ? (float)a.extractLaneF64(i) : 0.0f);
						push(result);
					}
				}
			}

			case VectorInstr.F64x2_Op_Instr(var op) -> {
				switch(op) {
					case VectorInstr.Splat() -> {
						double a = (double)pop();
						push(V128.splatF64(a));
					}

					case VectorInstr.ExtractLane(var laneIdx) -> {
						V128 a = (V128)pop();
						double result = a.extractLaneF64(laneIdx);
						push(result);
					}

					case VectorInstr.ReplaceLane(var laneIdx) -> {
						double b = (double)pop();
						V128 a = (V128)pop();
						V128 result = a.replaceLaneF64(laneIdx, b);
						push(result);
					}

					case VectorInstr.VFRelOp vfRelOp -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128 result = V128.build64(switch(vfRelOp) {
							case EQ -> i -> a.extractLaneF64(i) == b.extractLaneF64(i) ? -1 : 0;
							case NE -> i -> a.extractLaneF64(i) != b.extractLaneF64(i) ? -1 : 0;
							case LT -> i -> a.extractLaneF64(i) < b.extractLaneF64(i) ? -1 : 0;
							case GT -> i -> a.extractLaneF64(i) > b.extractLaneF64(i) ? -1 : 0;
							case LE -> i -> a.extractLaneF64(i) <= b.extractLaneF64(i) ? -1 : 0;
							case GE -> i -> a.extractLaneF64(i) >= b.extractLaneF64(i) ? -1 : 0;
						});

						push(result);
					}

					case VectorInstr.VFUnOp vfUnOp -> {
						V128 a = (V128)pop();

						V128.UnaryF64Function f = switch(vfUnOp) {
							case ABS -> Math::abs;
							case NEG -> n0 -> -n0;
							case SQRT -> Math::sqrt;
							case CEIL -> Util::ceilF64;
							case FLOOR -> Util::floorF64;
							case TRUNC -> Util::truncF64;
							case NEAREST -> Util::nearestF64;
						};

						push(a.unaryF64(f));
					}

					case VectorInstr.VFBinOp vfBinOp -> {
						V128 b = (V128)pop();
						V128 a = (V128)pop();

						V128.BinaryF64Function f = switch(vfBinOp) {
							case ADD -> Double::sum;
							case SUB -> (n0, n1) -> n0 - n1;
							case MUL -> (n0, n1) -> n0 * n1;
							case DIV -> (n0, n1) -> n0 / n1;
							case MIN -> Util::minF64;
							case MAX -> Util::maxF64;
							case PMIN -> (n0, n1) -> n1 < n0 ? n1 : n0;
							case PMAX -> (n0, n1) -> n0 < n1 ? n1 : n0;
						};

						push(a.binaryF64(b, f));
					}

					case VectorInstr.F64x2_Convert_Low_I32x4_U() -> {
						V128 a = (V128)pop();
						V128 result = V128.buildF64(i -> (double)Integer.toUnsignedLong(a.extractLane32(i)));
						push(result);
					}

					case VectorInstr.F64x2_Convert_Low_I32x4_S() -> {
						V128 a = (V128)pop();
						V128 result = V128.buildF64(i -> (double)a.extractLane32(i));
						push(result);
					}

					case VectorInstr.F64x2_Promote_Low_F32x4() -> {
						V128 a = (V128)pop();
						V128 result = V128.buildF64(i -> (double)a.extractLaneF32(i));
						push(result);
					}
				}
			}
		}
	}

	private void evaluateReferenceInstr(ReferenceInstr instr) throws Throwable {
		switch(instr) {
			case ReferenceInstr.Ref_Func refFunc -> {
				var func = module.getFunction(refFunc.func());
				push(func);
			}
			case ReferenceInstr.Ref_IsNull refIsNull -> {
				Object o = pop();
				int result = o == null ? 1 : 0;
				push(result);
			}

			case ReferenceInstr.Ref_Null refNull -> {
				push(null);
			}
		}
	}

	private void evaluateParametricInstruction(ParametricInstr instr) throws Throwable {
		switch(instr) {
			case ParametricInstr.Drop() -> {
				pop();
			}
			case ParametricInstr.Select select -> {
				int c = (int)pop();
				Object val2 = pop();
				Object val1 = pop();
				push(c != 0 ? val1 : val2);
			}
		}
	}

	private void evaluateVariableInstruction(VariableInstr instr) throws Throwable {
		switch(instr) {
			case VariableInstr.Local_Get(var local) -> {
				push(locals.get(local.index()));
			}
			case VariableInstr.Local_Set(var local) -> {
				locals.set(local.index(), pop());
			}
			case VariableInstr.Local_Tee(var local) -> {
				locals.set(local.index(), peek());
			}
			case VariableInstr.Global_Get(var global) -> {
				push(module.getGlobal(global).get());
			}
			case VariableInstr.Global_Set(var global) -> {
				module.getGlobal(global).set(pop());
			}
		}
	}

	private void evaluateTableInstruction(TableInstr instr) throws Throwable {
		switch(instr) {
			case TableInstr.Table_Get tableGet -> {
				int i = (int)pop();
				Object val = module.getTable(tableGet.table()).get(i);
				push(val);
			}

			case TableInstr.Table_Set tableSet -> {
				Object val = pop();
				int i = (int)pop();
				module.getTable(tableSet.table()).set(i, val);
			}

			case TableInstr.Table_Size tableSize -> {
				int size = module.getTable(tableSize.table()).size();
				push(size);
			}

			case TableInstr.Table_Grow tableGrow -> {
				int n = (int)pop();
				Object val = pop();
				int result = module.getTable(tableGrow.table()).grow(n, val);
				push(result);
			}

			case TableInstr.Table_Fill tableFill -> {
				WasmTable table = module.getTable(tableFill.table());

				int n = (int)pop();
				Object val = pop();
				int i = (int)pop();

				WasmTable.fill(n, val, i, table);
			}

			case TableInstr.Table_Copy tableCopy -> {
				WasmTable tableX = module.getTable(tableCopy.dest());
				WasmTable tableY = module.getTable(tableCopy.src());

				int n = (int)pop();
				int s = (int)pop();
				int d = (int)pop();

				WasmTable.copy(n, s, d, tableX, tableY);
			}

			case TableInstr.Table_Init tableInit -> {
				WasmTable table = module.getTable(tableInit.table());
				WasmElements elem = module.getElement(tableInit.elem());

				int n = (int)pop();
				int s = (int)pop();
				int d = (int)pop();

				WasmTable.init(d, s, n, table, elem);
			}

			case TableInstr.Elem_Drop elemDrop -> {
				module.dropElement(elemDrop.elem());
			}
		}
	}

	private void evaluateMemoryInstruction(MemoryInstr instr) throws Throwable {
		switch(instr) {
			case MemoryInstr.Inn_Load innLoad -> {
				var memory = module.getMemory(new MemIdx(0));
				int i = (int)pop();
				int address = calculateMemoryAddress(i, innLoad.memArg().offset());

				switch(innLoad.numSize()) {
					case _32 -> push(memory.loadI32(address));
					case _64 -> push(memory.loadI64(address));
				}
			}

			case MemoryInstr.Fnn_Load fnnLoad -> {
				var memory = module.getMemory(new MemIdx(0));
				int i = (int)pop();
				int address = calculateMemoryAddress(i, fnnLoad.memArg().offset());

				switch(fnnLoad.numSize()) {
					case _32 -> push(memory.loadF32(address));
					case _64 -> push(memory.loadF64(address));
				}
			}
			
			case MemoryInstr.Inn_Store innStore -> {
				var memory = module.getMemory(new MemIdx(0));
				Object value = pop();
				int i = (int)pop();
				int address = calculateMemoryAddress(i, innStore.memArg().offset());

				switch(innStore.numSize()) {
					case _32 -> memory.storeI32(address, (int)value);
					case _64 -> memory.storeI64(address, (long)value);
				}
			}

			case MemoryInstr.Fnn_Store fnnStore -> {
				var memory = module.getMemory(new MemIdx(0));
				Object value = pop();
				int i = (int)pop();
				int address = calculateMemoryAddress(i, fnnStore.memArg().offset());

				switch(fnnStore.numSize()) {
					case _32 -> memory.storeF32(address, (float)value);
					case _64 -> memory.storeF64(address, (double)value);
				}
			}

			case MemoryInstr.V128_Load v128Load -> {
				var memory = module.getMemory(new MemIdx(0));
				int i = (int)pop();
				int address = calculateMemoryAddress(i, v128Load.memArg().offset());
				push(memory.loadV128(address));
			}

			case MemoryInstr.V128_Store v128Store -> {
				var memory = module.getMemory(new MemIdx(0));
				V128 value = (V128)pop();
				int i = (int)pop();
				int address = calculateMemoryAddress(i, v128Store.memArg().offset());

				memory.storeV128(address, value);
			}

			case MemoryInstr.Inn_Load8_U innLoad8U -> {
				var memory = module.getMemory(new MemIdx(0));
				int i = (int)pop();
				int address = calculateMemoryAddress(i, innLoad8U.memArg().offset());
				byte value = memory.loadI8(address);

				switch(innLoad8U.numSize()) {
					case _32 -> push(Byte.toUnsignedInt(value));
					case _64 -> push(Byte.toUnsignedLong(value));
				}
			}

			case MemoryInstr.Inn_Load8_S innLoad8S -> {
				var memory = module.getMemory(new MemIdx(0));
				int i = (int)pop();
				int address = calculateMemoryAddress(i, innLoad8S.memArg().offset());
				byte value = memory.loadI8(address);

				switch(innLoad8S.numSize()) {
					case _32 -> push((int)value);
					case _64 -> push((long)value);
				}
			}

			case MemoryInstr.Inn_Load16_U innLoad16U -> {
				var memory = module.getMemory(new MemIdx(0));
				int i = (int)pop();
				int address = calculateMemoryAddress(i, innLoad16U.memArg().offset());
				short value = memory.loadI16(address);

				switch(innLoad16U.numSize()) {
					case _32 -> push(Short.toUnsignedInt(value));
					case _64 -> push(Short.toUnsignedLong(value));
				}
			}

			case MemoryInstr.Inn_Load16_S innLoad16S -> {
				var memory = module.getMemory(new MemIdx(0));
				int i = (int)pop();
				int address = calculateMemoryAddress(i, innLoad16S.memArg().offset());
				short value = memory.loadI16(address);

				switch(innLoad16S.numSize()) {
					case _32 -> push((int)value);
					case _64 -> push((long)value);
				}
			}

			case MemoryInstr.I64_Load32_U(var memArg) -> {
				var memory = module.getMemory(new MemIdx(0));
				int i = (int)pop();
				int address = calculateMemoryAddress(i, memArg.offset());
				int value = memory.loadI32(address);
				push(Integer.toUnsignedLong(value));
			}

			case MemoryInstr.I64_Load32_S(var memArg) -> {
				var memory = module.getMemory(new MemIdx(0));
				int i = (int)pop();
				int address = calculateMemoryAddress(i, memArg.offset());
				int value = memory.loadI32(address);
				push((long)value);
			}

			case MemoryInstr.Inn_Store8 innStore8 -> {
				var memory = module.getMemory(new MemIdx(0));
				Object value = pop();
				int i = (int)pop();
				int address = calculateMemoryAddress(i, innStore8.memArg().offset());

				byte numValue = switch(innStore8.numSize()) {
					case _32 -> (byte)(int)value;
					case _64 -> (byte)(long)value;
				};

				memory.storeI8(address, numValue);
			}

			case MemoryInstr.Inn_Store16 innStore16 -> {
				var memory = module.getMemory(new MemIdx(0));
				Object value = pop();
				int i = (int)pop();
				int address = calculateMemoryAddress(i, innStore16.memArg().offset());

				short numValue = switch(innStore16.numSize()) {
					case _32 -> (short)(int)value;
					case _64 -> (short)(long)value;
				};

				memory.storeI16(address, numValue);
			}

			case MemoryInstr.I64_Store32(var memArg) -> {
				var memory = module.getMemory(new MemIdx(0));
				int value = (int)(long)pop();
				int i = (int)pop();
				int address = calculateMemoryAddress(i, memArg.offset());

				memory.storeI32(address, value);
			}



			case MemoryInstr.V128_Load8x8_U(var memArg) -> {
				var memory = module.getMemory(new MemIdx(0));
				int i = (int)pop();
				int address = calculateMemoryAddress(i, memArg.offset());

				byte[] values = new byte[8];
				for(int j = 0; j < values.length; ++j) {
					values[j] = memory.loadI8(calculateMemoryAddress(address, j));
				}

				V128 result = V128.build16(j -> (short)Byte.toUnsignedInt(values[j]));
				push(result);
			}

			case MemoryInstr.V128_Load8x8_S(var memArg) -> {
				var memory = module.getMemory(new MemIdx(0));
				int i = (int)pop();
				int address = calculateMemoryAddress(i, memArg.offset());

				byte[] values = new byte[8];
				for(int j = 0; j < values.length; ++j) {
					values[j] = memory.loadI8(calculateMemoryAddress(address, j));
				}

				V128 result = V128.build16(j -> values[j]);
				push(result);
			}

			case MemoryInstr.V128_Load16x4_U(var memArg) -> {
				var memory = module.getMemory(new MemIdx(0));
				int i = (int)pop();
				int address = calculateMemoryAddress(i, memArg.offset());

				short[] values = new short[4];
				for(int j = 0; j < values.length; ++j) {
					values[j] = memory.loadI16(calculateMemoryAddress(address, j * 2));
				}

				V128 result = V128.build32(j -> Short.toUnsignedInt(values[j]));
				push(result);
			}

			case MemoryInstr.V128_Load16x4_S(var memArg) -> {
				var memory = module.getMemory(new MemIdx(0));
				int i = (int)pop();
				int address = calculateMemoryAddress(i, memArg.offset());

				short[] values = new short[4];
				for(int j = 0; j < values.length; ++j) {
					values[j] = memory.loadI16(calculateMemoryAddress(address, j * 2));
				}

				V128 result = V128.build32(j -> values[j]);
				push(result);
			}

			case MemoryInstr.V128_Load32x2_U(var memArg) -> {
				var memory = module.getMemory(new MemIdx(0));
				int i = (int)pop();
				int address = calculateMemoryAddress(i, memArg.offset());

				int[] values = new int[2];
				for(int j = 0; j < values.length; ++j) {
					values[j] = memory.loadI32(calculateMemoryAddress(address, j * 4));
				}

				V128 result = V128.build64(j -> Integer.toUnsignedLong(values[j]));
				push(result);
			}

			case MemoryInstr.V128_Load32x2_S(var memArg) -> {
				var memory = module.getMemory(new MemIdx(0));
				int i = (int)pop();
				int address = calculateMemoryAddress(i, memArg.offset());

				int[] values = new int[4];
				for(int j = 0; j < values.length; ++j) {
					values[j] = memory.loadI32(calculateMemoryAddress(address, j * 4));
				}

				V128 result = V128.build64(j -> values[j]);
				push(result);
			}

			case MemoryInstr.V128_Load32_Zero(var memArg) -> {
				var memory = module.getMemory(new MemIdx(0));
				int i = (int)pop();
				int address = calculateMemoryAddress(i, memArg.offset());

				int value = memory.loadI32(address);
				V128 result = V128.build32(j -> j == 0 ? value : 0);
				push(result);
			}

			case MemoryInstr.V128_Load64_Zero(var memArg) -> {
				var memory = module.getMemory(new MemIdx(0));
				int i = (int)pop();
				int address = calculateMemoryAddress(i, memArg.offset());

				long value = memory.loadI64(address);
				V128 result = V128.build64(j -> j == 0 ? value : 0);
				push(result);
			}

			case MemoryInstr.V128_Load8_Splat(var memArg) -> {
				var memory = module.getMemory(new MemIdx(0));
				int i = (int)pop();
				int address = calculateMemoryAddress(i, memArg.offset());
				byte value = memory.loadI8(address);
				V128 result = V128.splat8(value);
				push(result);
			}

			case MemoryInstr.V128_Load16_Splat(var memArg) -> {
				var memory = module.getMemory(new MemIdx(0));
				int i = (int)pop();
				int address = calculateMemoryAddress(i, memArg.offset());
				short value = memory.loadI16(address);
				V128 result = V128.splat16(value);
				push(result);
			}

			case MemoryInstr.V128_Load32_Splat(var memArg) -> {
				var memory = module.getMemory(new MemIdx(0));
				int i = (int)pop();
				int address = calculateMemoryAddress(i, memArg.offset());
				int value = memory.loadI32(address);
				V128 result = V128.splat32(value);
				push(result);
			}

			case MemoryInstr.V128_Load64_Splat(var memArg) -> {
				var memory = module.getMemory(new MemIdx(0));
				int i = (int)pop();
				int address = calculateMemoryAddress(i, memArg.offset());
				long value = memory.loadI64(address);
				V128 result = V128.splat64(value);
				push(result);
			}

			case MemoryInstr.V128_Load8_Lane(var memArg, var laneIdx) -> {
				var memory = module.getMemory(new MemIdx(0));
				V128 v = (V128)pop();
				int i = (int)pop();
				int address = calculateMemoryAddress(i, memArg.offset());
				byte value = memory.loadI8(address);
				V128 result = v.replaceLane8(laneIdx, value);
				push(result);
			}

			case MemoryInstr.V128_Load16_Lane(var memArg, var laneIdx) -> {
				var memory = module.getMemory(new MemIdx(0));
				V128 v = (V128)pop();
				int i = (int)pop();
				int address = calculateMemoryAddress(i, memArg.offset());
				short value = memory.loadI16(address);
				V128 result = v.replaceLane16(laneIdx, value);
				push(result);
			}

			case MemoryInstr.V128_Load32_Lane(var memArg, var laneIdx) -> {
				var memory = module.getMemory(new MemIdx(0));
				V128 v = (V128)pop();
				int i = (int)pop();
				int address = calculateMemoryAddress(i, memArg.offset());
				int value = memory.loadI32(address);
				V128 result = v.replaceLane32(laneIdx, value);
				push(result);
			}

			case MemoryInstr.V128_Load64_Lane(var memArg, var laneIdx) -> {
				var memory = module.getMemory(new MemIdx(0));
				V128 v = (V128)pop();
				int i = (int)pop();
				int address = calculateMemoryAddress(i, memArg.offset());
				long value = memory.loadI64(address);
				V128 result = v.replaceLane64(laneIdx, value);
				push(result);
			}

			case MemoryInstr.V128_Store8_Lane(var memArg, var laneIdx) -> {
				var memory = module.getMemory(new MemIdx(0));
				V128 v = (V128)pop();
				int i = (int)pop();
				int address = calculateMemoryAddress(i, memArg.offset());
				memory.storeI8(address, v.extractLane8(laneIdx));
			}

			case MemoryInstr.V128_Store16_Lane(var memArg, var laneIdx) -> {
				var memory = module.getMemory(new MemIdx(0));
				V128 v = (V128)pop();
				int i = (int)pop();
				int address = calculateMemoryAddress(i, memArg.offset());
				memory.storeI16(address, v.extractLane16(laneIdx));
			}
			case MemoryInstr.V128_Store32_Lane(var memArg, var laneIdx) -> {
				var memory = module.getMemory(new MemIdx(0));
				V128 v = (V128)pop();
				int i = (int)pop();
				int address = calculateMemoryAddress(i, memArg.offset());
				memory.storeI32(address, v.extractLane32(laneIdx));
			}
			case MemoryInstr.V128_Store64_Lane(var memArg, var laneIdx) -> {
				var memory = module.getMemory(new MemIdx(0));
				V128 v = (V128)pop();
				int i = (int)pop();
				int address = calculateMemoryAddress(i, memArg.offset());
				memory.storeI64(address, v.extractLane64(laneIdx));
			}

			case MemoryInstr.Memory_Size() -> {
				int size = module.getMemory(new MemIdx(0)).pageSize();
				push(size);
			}

			case MemoryInstr.Memory_Grow() -> {
				int n = (int)pop();
				int growRes = module.getMemory(new MemIdx(0)).grow(n);
				push(growRes);
			}

			case MemoryInstr.Memory_Fill() -> {
				var memory = module.getMemory(new MemIdx(0));
				int n = (int)pop();
				byte val = (byte)(int)pop();
				int d = (int)pop();

				if(!Util.sumInRange(d, n, memory.byteSize())) {
					throw new IndexOutOfBoundsException();
				}

				while(n != 0) {
					memory.storeI8(d, val);
					++d;
					--n;
				}
			}

			case MemoryInstr.Memory_Copy() -> {
				var memory = module.getMemory(new MemIdx(0));
				int n = (int)pop();
				int s = (int)pop();
				int d = (int)pop();

				if(!Util.sumInRange(d, n, memory.byteSize()) || !Util.sumInRange(s, n, memory.byteSize())) {
					throw new IndexOutOfBoundsException();
				}

				if(d <= s) {
					while(n != 0) {
						byte b = memory.loadI8(s);
						memory.storeI8(d, b);
						++d;
						++s;
						--n;
					}
				}
				else {
					while(n != 0) {
						byte b = memory.loadI8(s + n - 1);
						memory.storeI8(d + n - 1, b);
						--n;
					}
				}
			}

			case MemoryInstr.Memory_Init(var dataIdx) -> {
				var memory = module.getMemory(new MemIdx(0));
				var data = module.getData(dataIdx);
				int n = (int)pop();
				int s = (int)pop();
				int d = (int)pop();

				memory.init(d, s, n, data);
			}


			case MemoryInstr.Data_Drop(var dataIdx) -> module.dropData(dataIdx);
		}
	}

	private int calculateMemoryAddress(int address, int offset) {
		if(
				(
						offset < 0 && address < offset
				) ||
				(
						Integer.compareUnsigned(address + offset, address) < 0 ||
						Integer.compareUnsigned(address + offset, offset) < 0
				)
		) {
			throw new IndexOutOfBoundsException();
		}

		return address + offset;
	}

	private FunctionResult evaluateControlInstruction(ControlInstr instr) throws Throwable {
		return switch(instr) {
			case ControlInstr.Nop() -> null;
			case ControlInstr.Unreachable() -> throw new UnreachableException();
			case ControlInstr.Block(var type, var innerBlock) -> {
				enterBlock(type, innerBlock, ip + 1, true);
				yield null;
			}
			case ControlInstr.Loop(var type, var innerBlock) -> {
				enterBlock(type, innerBlock, ip, false);
				yield null;
			}
			case ControlInstr.If(var type, var thenBlock, var elseBlock) -> {
				int value = (int)pop();
				var innerBlock = value != 0 ? thenBlock : elseBlock;
				enterBlock(type, innerBlock, ip + 1, true);
				yield null;
			}
			case ControlInstr.Br(var label) -> {
				branch(label.index());
				yield null;
			}
			case ControlInstr.Br_If(var label) -> {
				int value = (int)pop();
				if(value != 0) {
					branch(label.index());
				}
				yield null;
			}
			case ControlInstr.Br_Table(var table, var fallback) -> {
				int value = (int)pop();

				LabelIdx labelIdx;
				if(value >= 0 && value < table.size()) {
					labelIdx = table.get(value);
				}
				else {
					labelIdx = fallback;
				}

				branch(labelIdx.index());
				yield null;
			}
			case ControlInstr.Return() -> {
				var result = getTopValues(topBlockType.results().types().size());
				yield new FunctionResult.Values(result);
			}
			case ControlInstr.Call(var funcIdx) -> {
				var func = module.getFunction(funcIdx);
				var args = getTopValues(func.type().args().types().size());
				Object[] results = func.invokeNow(args);
				pushAll(results);
				yield null;
			}
			case ControlInstr.Call_Indirect(var tableIdx, var funcTypeIdx) -> {
				int index = (int)pop();

				var funcType = module.getType(funcTypeIdx);
				var func = (WasmFunction)module.getTable(tableIdx).get(index);

				if(!func.type().equals(funcType)) {
					throw new IndirectCallTypeMismatchException();
				}

				var args = getTopValues(func.type().args().types().size());
				Object[] results = func.invokeNow(args);
				pushAll(results);
				yield null;
			}
			case ControlInstr.Return_Call(var funcIdx) -> {
				var func = module.getFunction(funcIdx);
				var args = getTopValues(func.type().args().types().size());
				yield (FunctionResult.Delay)() -> func.invoke(args);
			}
			case ControlInstr.Return_Call_Indirect(var tableIdx, var funcTypeIdx) -> {
				int index = (int)pop();

				var funcType = module.getType(funcTypeIdx);
				var func = (WasmFunction)module.getTable(tableIdx).get(index);

				if(!func.type().equals(funcType)) {
					throw new IndirectCallTypeMismatchException();
				}

				var args = getTopValues(func.type().args().types().size());
				yield (FunctionResult.Delay)() -> func.invoke(args);
			}
		};
	}



	private FuncType expandBlockType(ControlInstr.BlockType blockType) {
		return switch(blockType) {
			case ControlInstr.BlockType.Empty() -> new FuncType(new ResultType(List.of()), new ResultType(List.of()));
			case ControlInstr.BlockType.OfIndex(var index) -> module.getType(index);
			case ControlInstr.BlockType.OfValType(var valType) -> new FuncType(new ResultType(List.of()), new ResultType(List.of(valType)));
		};
	}

	private void enterBlock(ControlInstr.BlockType type, List<? extends Instr> innerBlock, int branchIP, boolean useResultType) {
		var expandedType = expandBlockType(type);
		var label = new Label(block, blockType, useResultType ? expandedType.results() : expandedType.args(), branchIP, ip + 1);

		Object[] values = getTopValues(expandedType.args().types().size());

		push(label);

		for(Object value : values) {
			push(value);
		}

		block = innerBlock;
		blockType = expandedType;
		ip = -1; // -1 accounts for upcoming increment to set ip to 0
	}

	private Label getLabel(int n) {
		for(int i = stack.size() - 1; i >= 0; --i) {
			if(stack.get(i) instanceof Label label) {
				if(n > 0) {
					--n;
				}
				else {
					return label;
				}
			}
		}

		if(n > 0) {
			throw new IllegalStateException();
		}
		else {
			return new Label(List.of(), topBlockType, topBlockType.results(), 0, 0);
		}
	}

	private void branch(int n) {
		var label = getLabel(n);
		Object[] values = getTopValues(label.resultType().types().size());

		while(true) {
			if(n == 0 && stack.isEmpty()) {
				break;
			}

			if(pop() instanceof Label) {
				if(n > 0) {
					--n;
				}
				else {
					break;
				}
			}
		}

		pushAll(values);

		block = label.block;
		blockType = label.outerBlockType;
		ip = label.branchIndex - 1; // -1 accounts for upcoming increment to set ip to label.index
	}

}
