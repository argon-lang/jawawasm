package dev.argon.jawawasm.engine.interpreter;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import dev.argon.jawawasm.runtime.*;
import dev.argon.jawawasm.format.instructions.*;
import dev.argon.jawawasm.format.modules.Func;
import dev.argon.jawawasm.format.modules.LabelIdx;
import dev.argon.jawawasm.format.types.*;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;

class StackFrame {

	public StackFrame(InstantiatedModule module, Func func, @Nullable Object[] args) {
		this.module = module;
		block = func.body().body();
		topBlockType = module.getFuncType(func.type());
		blockType = topBlockType;

		if(args.length != topBlockType.args().types().size()) {
			throw new IllegalArgumentException("Argument length did not match the expected parameters");
		}

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
	private final List<@Nullable Object> locals;
	private final ArrayList<@Nullable Object> stack = new ArrayList<>();
	
	private void push(@Nullable Object value) {
		stack.add(value);
	}
	
	private @Nullable Object pop() {
		Object value = stack.getLast();
		stack.removeLast();
		return value;
	}

	private Object popNonNull() {
		Object value = pop();
		Objects.requireNonNull(value);
		return value;
	}
	
	private int popInt() {
		return (int)popNonNull();
	}

	private long popLong() {
		return (long)popNonNull();
	}

	private float popFloat() {
		return (float)popNonNull();
	}

	private double popDouble() {
		return (double)popNonNull();
	}

	private V128 popV128() {
		return (V128)popNonNull();
	}

	private @Nullable Object peek() {
		return stack.getLast();
	}

	private @Nullable Object[] getTopValues(int n) {
		@Nullable Object[] values = new Object[n];
		for(int i = values.length - 1; i >= 0; --i) {
			values[i] = pop();
		}
		return values;
	}

	private void pushAll(@Nullable Object[] values) {
		for(var value : values) {
			push(value);
		}
	}

	public DynamicFunctionResult evaluate() throws Throwable {
		while(true) {
			for(; ip < block.size(); ++ip) {
				try {
					DynamicFunctionResult result = null;

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
				catch(DynamicWebAssemblyException ex) {
					handleException(ex);
				}
			}

			@Nullable Object[] values = getTopValues(blockType.results().types().size());

			if(stack.isEmpty()) {
				return new DynamicFunctionResult.Values(values);
			}

			var label = (Label)popNonNull();
			block = label.block;
			blockType = label.outerBlockType;
			ip = label.endIndex;

			if(!stack.isEmpty() && peek() instanceof ExceptionHandler) pop();

			pushAll(values);
		}
	}

	private record Label(List<? extends Instr> block, FuncType outerBlockType, ResultType resultType, int branchIndex, int endIndex) {
		@Override
		public String toString() {
			return "Label[outerBlockType=" + outerBlockType + ", resultType=" + resultType + ", branchIndex=" + branchIndex + ", endIndex=" + endIndex + "]";
		}
	}

	private record ExceptionHandler(List<? extends ControlInstr.CatchClause> catchClauses) {}

	private void evaluateNumInstr(NumericInstr instr) {
		switch(instr) {
			case NumericInstr.I32_Const(var value) -> push(value);
			case NumericInstr.I64_Const(var value) -> push(value);
			case NumericInstr.F32_Const(var value) -> push(value);
			case NumericInstr.F64_Const(var value) -> push(value);

			case NumericInstr.Inn_IUnOp innIUnOp -> {
				switch(innIUnOp.size()) {
					case _32 -> {
						int a = popInt();

						int result = switch(innIUnOp.op()) {
							case CLZ -> Integer.numberOfLeadingZeros(a);
							case CTZ -> Integer.numberOfTrailingZeros(a);
							case POPCNT -> Integer.bitCount(a);
						};
						push(result);
					}
					case _64 -> {
						long a = popLong();

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
						float a = popFloat();

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
						double a = popDouble();

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
						int b = popInt();
						int a = popInt();

						int result = switch(iBinOpInstr.op()) {
							case ADD -> a + b;
							case SUB -> a - b;
							case MUL -> a * b;
							case DIV_U -> Integer.divideUnsigned(a, b);
							case DIV_S -> Util.divideS32(a, b);
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
						long b = popLong();
						long a = popLong();

						long result = switch(iBinOpInstr.op()) {
							case ADD -> a + b;
							case SUB -> a - b;
							case MUL -> a * b;
							case DIV_U -> Long.divideUnsigned(a, b);
							case DIV_S -> Util.divideS64(a, b);
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
						float b = popFloat();
						float a = popFloat();

						float result = switch(fnnFBinOp.op()) {
							case ADD -> a + b;
							case SUB -> a - b;
							case MUL -> a * b;
							case DIV -> a / b;
							case MIN -> Util.min(a, b);
							case MAX -> Util.max(a, b);
							case COPYSIGN -> Math.copySign(a, b);
						};
						push(result);
					}
					case _64 -> {
						double b = popDouble();
						double a = popDouble();

						double result = switch(fnnFBinOp.op()) {
							case ADD -> a + b;
							case SUB -> a - b;
							case MUL -> a * b;
							case DIV -> a / b;
							case MIN -> Util.min(a, b);
							case MAX -> Util.max(a, b);
							case COPYSIGN -> Math.copySign(a, b);
						};
						push(result);
					}
				}
			}

			case NumericInstr.Inn_ITestOp innITestOp -> {
				switch(innITestOp.size()) {
					case _32 -> {
						int a = popInt();

						boolean result = switch(innITestOp.op()) {
							case EQZ -> a == 0;
						};
						push(result ? 1 : 0);
					}
					case _64 -> {
						long a = popLong();

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
						int b = popInt();
						int a = popInt();

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
						long b = popLong();
						long a = popLong();

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
						float b = popFloat();
						float a = popFloat();

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
						double b = popDouble();
						double a = popDouble();

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
						int a = popInt();
						push((int)(byte)a);
					}
					case _64 -> {
						long a = popLong();
						push((long)(byte)a);
					}
				}
			}

			case NumericInstr.Inn_Extend16_S innExtend16S -> {
				switch(innExtend16S.size()) {
					case _32 -> {
						int a = popInt();
						push((int)(short)a);
					}
					case _64 -> {
						long a = popLong();
						push((long)(short)a);
					}
				}
			}

			case NumericInstr.I64_Extend32_S() -> {
				long a = popLong();
				push((long)(int)a);
			}

			case NumericInstr.I32_Wrap_I64() -> {
				long a = popLong();
				push((int)a);
			}

			case NumericInstr.I64_Extend_I32_S() -> {
				int a = popInt();
				push((long)a);
			}

			case NumericInstr.I64_Extend_I32_U() -> {
				int a = popInt();
				push(Integer.toUnsignedLong(a));
			}

			case NumericInstr.Inn_Trunc_Fmm_S innTruncFmmS -> {
				BigDecimal value = switch(innTruncFmmS.floatSize()) {
					case _32 -> {
						float a = popFloat();
						if(!Float.isFinite(a)) {
							throw new ArithmeticException();
						}

						yield new BigDecimal(a);
					}
					case _64 -> {
						double a = popDouble();
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
						float a = popFloat();
						if(!Float.isFinite(a) || a <= -1.0f) {
							throw new ArithmeticException();
						}

						yield new BigDecimal(a);
					}
					case _64 -> {
						double a = popDouble();
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
						float a = popFloat();

						switch(innTruncSatFmmS.intSize()) {
							case _32 -> push((int)a);
							case _64 -> push((long)a);
						}
					}
					case _64 -> {
						double a = popDouble();

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
						float a = popFloat();

						switch(innTruncSatFmmU.intSize()) {
							case _32 -> push(Util.truncSatF32U32(a));
							case _64 -> push(Util.truncSatF32U64(a));
						}
					}
					case _64 -> {
						double a = popDouble();

						switch(innTruncSatFmmU.intSize()) {
							case _32 -> push(Util.truncSatF64U32(a));
							case _64 -> push(Util.truncSatF64U64(a));
						}
					}
				}
			}

			case NumericInstr.F32_Demote_F64() -> {
				double a = popDouble();
				push((float)a);
			}
			case NumericInstr.F64_Promote_F32() -> {
				float a = popFloat();
				push((double)a);
			}

			case NumericInstr.Fnn_Convert_Imm_S fnnConvertImmS -> {
				switch(fnnConvertImmS.floatSize()) {
					case _32 -> {
						float result = switch(fnnConvertImmS.intSize()) {
							case _32 -> (float)popInt();
							case _64 -> (float)popLong();
						};
						push(result);
					}
					case _64 -> {
						double result = switch(fnnConvertImmS.intSize()) {
							case _32 -> (double)popInt();
							case _64 -> (double)popLong();
						};
						push(result);
					}
				}
			}

			case NumericInstr.Fnn_Convert_Imm_U fnnConvertImmU -> {
				switch(fnnConvertImmU.floatSize()) {
					case _32 -> {
						float result = switch(fnnConvertImmU.intSize()) {
							case _32 -> (float)Integer.toUnsignedLong(popInt());
							case _64 -> Util.u64ToF32(popLong());
						};
						push(result);
					}
					case _64 -> {
						double result = switch(fnnConvertImmU.intSize()) {
							case _32 -> (double)Integer.toUnsignedLong(popInt());
							case _64 -> new BigDecimal(Long.toUnsignedString(popLong())).doubleValue();
						};
						push(result);
					}
				}
			}

			case NumericInstr.Fnn_Reinterpret_Inn fnnReinterpretInn -> {
				switch(fnnReinterpretInn.size()) {
					case _32 -> {
						int a = popInt();
						float result = Float.intBitsToFloat(a);
						push(result);
					}
					case _64 -> {
						long a = popLong();
						double result = Double.longBitsToDouble(a);
						push(result);
					}
				}
			}

			case NumericInstr.Inn_Reinterpret_Fnn innReinterpretFnn -> {
				switch(innReinterpretFnn.size()) {
					case _32 -> {
						float a = popFloat();
						int result = Float.floatToRawIntBits(a);
						push(result);
					}
					case _64 -> {
						double a = popDouble();
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
				V128 a = popV128();

				V128.Unary8Function f = switch(vvUnOp) {
					case NOT -> b -> (byte)~b;

				};


				V128 result = a.unary8(f);

				push(result);
			}

			case VectorInstr.VVBinOp vvBinOp -> {
				V128 b = popV128();
				V128 a = popV128();

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
				V128 c = popV128();
				V128 b = popV128();
				V128 a = popV128();

				V128.Ternary8Function f = switch(vvTernOp) {
					case BITSELECT -> (b0, b1, b2) -> (byte)((b0 & b2) | (b1 & ~b2));
				};

				V128 result = a.ternary8(b, c, f);
				push(result);
			}

			case VectorInstr.VVTestOp vvTestOp -> {
				V128 a = popV128();

				int result = switch(vvTestOp) {
					case ANY_TRUE -> a.anyTrue() ? 1 : 0;
				};

				push(result);
			}

			case VectorInstr.I8x16_Op_Instr(var op) -> {
				switch(op) {
					case VectorInstr.Swizzle() -> {
						V128 b = popV128();
						V128 a = popV128();
						push(a.swizzle8(b));
					}

					case VectorInstr.Shuffle(var laneIndexes) -> {
						V128 b = popV128();
						V128 a = popV128();
						push(laneIndexes.shuffle8(a, b));
					}

					case VectorInstr.Splat() -> {
						int a = popInt();
						push(V128.splat8((byte)a));
					}

					case VectorInstr.ExtractLane_U(var laneIdx) -> {
						V128 a = popV128();
						byte result = a.extractLane8(laneIdx);
						push(Byte.toUnsignedInt(result));
					}

					case VectorInstr.ExtractLane_S(var laneIdx) -> {
						V128 a = popV128();
						byte result = a.extractLane8(laneIdx);
						push((int)result);
					}

					case VectorInstr.ReplaceLane(var laneIdx) -> {
						int b = popInt();
						V128 a = popV128();
						V128 result = a.replaceLane8(laneIdx, (byte)b);
						push(result);
					}

					case VectorInstr.VIRelOp viRelOp -> {
						V128 b = popV128();
						V128 a = popV128();

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
						V128 a = popV128();

						V128.Unary8Function f = switch(viUnOp) {
							case ABS -> n0 -> (byte)Math.abs(n0);
							case NEG -> n0 -> (byte)-n0;
						};

						push(a.unary8(f));
					}

					case VectorInstr.Popcnt() -> {
						V128 a = popV128();
						push(a.unary8(n0 -> (byte)Integer.bitCount(Byte.toUnsignedInt(n0))));
					}

					case VectorInstr.All_True() -> {
						V128 a = popV128();
						boolean result = a.allTrue8();
						push(result ? 1 : 0);
					}

					case VectorInstr.BitMask() -> {
						V128 a = popV128();
						int result = a.bitmask8();
						push(result);
					}

					case VectorInstr.I8x16_Narrow_I16x8_U() -> {
						V128 b = popV128();
						V128 a = popV128();

						V128 result = V128.build8(i -> Util.narrowU16I8(i < 8 ? a.extractLane16(i) : b.extractLane16(i - 8)));
						push(result);
					}

					case VectorInstr.I8x16_Narrow_I16x8_S() -> {
						V128 b = popV128();
						V128 a = popV128();

						V128 result = V128.build8(i -> Util.narrowS16I8(i < 8 ? a.extractLane16(i) : b.extractLane16(i - 8)));
						push(result);
					}

					case VectorInstr.VIShiftOp viShiftOp -> {
						int b = popInt() & 0x07;
						V128 a = popV128();

						V128.Unary8Function f = switch(viShiftOp) {
							case SHL -> n0 -> (byte)(n0 << b);
							case SHR_U -> n0 -> (byte)(Byte.toUnsignedInt(n0) >>> b);
							case SHR_S -> n0 -> (byte)(n0 >> b);
						};

						push(a.unary8(f));
					}

					case VectorInstr.VIBinOp viBinOp -> {
						V128 b = popV128();
						V128 a = popV128();

						V128.Binary8Function f = switch(viBinOp) {
							case ADD -> (n0, n1) -> (byte)(n0 + n1);
							case SUB -> (n0, n1) -> (byte)(n0 - n1);
						};

						push(a.binary8(b, f));
					}

					case VectorInstr.VIMinMaxOp viBinOp -> {
						V128 b = popV128();
						V128 a = popV128();

						V128.Binary8Function f = switch(viBinOp) {
							case MIN_U -> (n0, n1) -> (byte)Math.min(Byte.toUnsignedInt(n0), Byte.toUnsignedInt(n1));
							case MIN_S -> (n0, n1) -> (byte)Math.min(n0, n1);
							case MAX_U -> (n0, n1) -> (byte)Math.max(Byte.toUnsignedInt(n0), Byte.toUnsignedInt(n1));
							case MAX_S -> (n0, n1) -> (byte)Math.max(n0, n1);
						};

						push(a.binary8(b, f));
					}

					case VectorInstr.VISatBinOp viSatBinOp -> {
						V128 b = popV128();
						V128 a = popV128();

						V128.Binary8Function f = switch(viSatBinOp) {
							case ADD_SAT_U -> Util::addSatU8;
							case ADD_SAT_S -> Util::addSatS8;
							case SUB_SAT_U -> Util::subSatU8;
							case SUB_SAT_S -> Util::subSatS8;
						};

						push(a.binary8(b, f));
					}

					case VectorInstr.VIAverageOps viAverageOps -> {
						V128 b = popV128();
						V128 a = popV128();

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
						int a = popInt();
						push(V128.splat16((short)a));
					}

					case VectorInstr.ExtractLane_U(var laneIdx) -> {
						V128 a = popV128();
						short result = a.extractLane16(laneIdx);
						push(Short.toUnsignedInt(result));
					}

					case VectorInstr.ExtractLane_S(var laneIdx) -> {
						V128 a = popV128();
						short result = a.extractLane16(laneIdx);
						push((int)result);
					}

					case VectorInstr.ReplaceLane(var laneIdx) -> {
						int b = popInt();
						V128 a = popV128();
						V128 result = a.replaceLane16(laneIdx, (short)b);
						push(result);
					}

					case VectorInstr.VIRelOp viRelOp -> {
						V128 b = popV128();
						V128 a = popV128();

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
						V128 a = popV128();

						V128.Unary16Function f = switch(viUnOp) {
							case ABS -> n0 -> (short)Math.abs(n0);
							case NEG -> n0 -> (short)-n0;
						};

						push(a.unary16(f));
					}

					case VectorInstr.Q15mulr_Sat_S() -> {
						V128 b = popV128();
						V128 a = popV128();

						V128 result = a.binary16(b, (n0, n1) -> Util.narrowS32I16((n0 * n1 + (1 << 14)) >> 15));

						push(result);
					}

					case VectorInstr.All_True() -> {
						V128 a = popV128();
						boolean result = a.allTrue16();
						push(result ? 1 : 0);
					}

					case VectorInstr.BitMask() -> {
						V128 a = popV128();
						int result = a.bitmask16();
						push(result);
					}

					case VectorInstr.I16x8_Narrow_I32x4_U() -> {
						V128 b = popV128();
						V128 a = popV128();

						V128 result = V128.build16(i -> Util.narrowU32I16(i < 4 ? a.extractLane32(i) : b.extractLane32(i - 4)));
						push(result);
					}

					case VectorInstr.I16x8_Narrow_I32x4_S() -> {
						V128 b = popV128();
						V128 a = popV128();

						V128 result = V128.build16(i -> Util.narrowS32I16(i < 4 ? a.extractLane32(i) : b.extractLane32(i - 4)));
						push(result);
					}

					case VectorInstr.I16x8_Extend_Low_I8x16_U() -> {
						V128 a = popV128();
						V128 result = V128.build16(i -> (short)Byte.toUnsignedInt(a.extractLane8(i)));
						push(result);
					}

					case VectorInstr.I16x8_Extend_Low_I8x16_S() -> {
						V128 a = popV128();
						V128 result = V128.build16(i -> (short)a.extractLane8(i));
						push(result);
					}

					case VectorInstr.I16x8_Extend_High_I8x16_U() -> {
						V128 a = popV128();
						V128 result = V128.build16(i -> (short)Byte.toUnsignedInt(a.extractLane8(i + 8)));
						push(result);
					}

					case VectorInstr.I16x8_Extend_High_I8x16_S() -> {
						V128 a = popV128();
						V128 result = V128.build16(i -> (short)a.extractLane8(i + 8));
						push(result);
					}

					case VectorInstr.VIShiftOp viShiftOp -> {
						int b = popInt() & 0x0F;
						V128 a = popV128();

						V128.Unary16Function f = switch(viShiftOp) {
							case SHL -> n0 -> (short)(n0 << b);
							case SHR_U -> n0 -> (short)(Short.toUnsignedInt(n0) >>> b);
							case SHR_S -> n0 -> (short)(n0 >> b);
						};

						push(a.unary16(f));
					}

					case VectorInstr.VIBinOp viBinOp -> {
						V128 b = popV128();
						V128 a = popV128();

						V128.Binary16Function f = switch(viBinOp) {
							case ADD -> (n0, n1) -> (short)(n0 + n1);
							case SUB -> (n0, n1) -> (short)(n0 - n1);
						};

						push(a.binary16(b, f));
					}

					case VectorInstr.VIMinMaxOp viBinOp -> {
						V128 b = popV128();
						V128 a = popV128();

						V128.Binary16Function f = switch(viBinOp) {
							case MIN_U -> (n0, n1) -> (short)Math.min(Short.toUnsignedInt(n0), Short.toUnsignedInt(n1));
							case MIN_S -> (n0, n1) -> (short)Math.min(n0, n1);
							case MAX_U -> (n0, n1) -> (short)Math.max(Short.toUnsignedInt(n0), Short.toUnsignedInt(n1));
							case MAX_S -> (n0, n1) -> (short)Math.max(n0, n1);
						};

						push(a.binary16(b, f));
					}

					case VectorInstr.VISatBinOp viSatBinOp -> {
						V128 b = popV128();
						V128 a = popV128();

						V128.Binary16Function f = switch(viSatBinOp) {
							case ADD_SAT_U -> Util::addSatU16;
							case ADD_SAT_S -> Util::addSatS16;
							case SUB_SAT_U -> Util::subSatU16;
							case SUB_SAT_S -> Util::subSatS16;
						};

						push(a.binary16(b, f));
					}

					case VectorInstr.VIMulOp viMulOp -> {
						V128 b = popV128();
						V128 a = popV128();

						V128.Binary16Function f = switch(viMulOp) {
							case MUL -> (n0, n1) -> (short)(n0 * n1);
						};

						push(a.binary16(b, f));
					}

					case VectorInstr.I16x8_ExtMul_Low_I8x16_U() -> {
						V128 b = popV128();
						V128 a = popV128();

						V128 result = V128.build16(i -> (short)(Byte.toUnsignedInt(a.extractLane8(i)) * Byte.toUnsignedInt(b.extractLane8(i))));

						push(result);
					}

					case VectorInstr.I16x8_ExtMul_Low_I8x16_S() -> {
						V128 b = popV128();
						V128 a = popV128();

						V128 result = V128.build16(i -> (short)(a.extractLane8(i) * b.extractLane8(i)));

						push(result);
					}

					case VectorInstr.I16x8_ExtMul_High_I8x16_U() -> {
						V128 b = popV128();
						V128 a = popV128();

						V128 result = V128.build16(i -> (short)(Byte.toUnsignedInt(a.extractLane8(i + 8)) * Byte.toUnsignedInt(b.extractLane8(i + 8))));

						push(result);
					}

					case VectorInstr.I16x8_ExtMul_High_I8x16_S() -> {
						V128 b = popV128();
						V128 a = popV128();

						V128 result = V128.build16(i -> (short)(a.extractLane8(i + 8) * b.extractLane8(i + 8)));

						push(result);
					}

					case VectorInstr.VIAverageOps viAverageOps -> {
						V128 b = popV128();
						V128 a = popV128();

						V128.Binary16Function f = switch(viAverageOps) {
							case AVGR_U -> (n0, n1) -> (short)((Short.toUnsignedInt(n0) + Short.toUnsignedInt(n1) + 1) / 2);
						};

						push(a.binary16(b, f));
					}

					case VectorInstr.I16x8_ExtAdd_Pairwise_I8x16_U() -> {
						V128 a = popV128();

						V128 result = V128.build16(i -> (short)(Byte.toUnsignedInt(a.extractLane8(i)) + Byte.toUnsignedInt(a.extractLane8(i + 8))));

						push(result);
					}

					case VectorInstr.I16x8_ExtAdd_Pairwise_I8x16_S() -> {
						V128 a = popV128();

						V128 result = V128.build16(i -> (short)(a.extractLane8(i) + a.extractLane8(i + 8)));

						push(result);
					}
				}
			}

			case VectorInstr.I32x4_Op_Instr(var op) -> {
				switch(op) {
					case VectorInstr.Splat() -> {
						int a = popInt();
						push(V128.splat32(a));
					}

					case VectorInstr.ExtractLane(var laneIdx) -> {
						V128 a = popV128();
						int result = a.extractLane32(laneIdx);
						push(result);
					}

					case VectorInstr.ReplaceLane(var laneIdx) -> {
						int b = popInt();
						V128 a = popV128();
						V128 result = a.replaceLane32(laneIdx, b);
						push(result);
					}

					case VectorInstr.VIRelOp viRelOp -> {
						V128 b = popV128();
						V128 a = popV128();

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
						V128 a = popV128();

						V128.Unary32Function f = switch(viUnOp) {
							case ABS -> Math::abs;
							case NEG -> n0 -> -n0;
						};

						push(a.unary32(f));
					}

					case VectorInstr.Dot_I16x8_S() -> {
						V128 b = popV128();
						V128 a = popV128();
						V128 result = V128.build32(i -> a.extractLane16(i) * b.extractLane16(i) + a.extractLane16(i + 4) * b.extractLane16(i + 4));
						push(result);
					}

					case VectorInstr.All_True() -> {
						V128 a = popV128();
						boolean result = a.allTrue32();
						push(result ? 1 : 0);
					}

					case VectorInstr.BitMask() -> {
						V128 a = popV128();
						int result = a.bitmask32();
						push(result);
					}

					case VectorInstr.I32x4_Extend_Low_I16x8_U() -> {
						V128 a = popV128();
						V128 result = V128.build32(i -> Short.toUnsignedInt(a.extractLane16(i)));
						push(result);
					}

					case VectorInstr.I32x4_Extend_Low_I16x8_S() -> {
						V128 a = popV128();
						V128 result = V128.build32(a::extractLane16);
						push(result);
					}

					case VectorInstr.I32x4_Extend_High_I16x8_U() -> {
						V128 a = popV128();
						V128 result = V128.build32(i -> Short.toUnsignedInt(a.extractLane16(i + 4)));
						push(result);
					}

					case VectorInstr.I32x4_Extend_High_I16x8_S() -> {
						V128 a = popV128();
						V128 result = V128.build32(i -> a.extractLane16(i + 4));
						push(result);
					}

					case VectorInstr.VIShiftOp viShiftOp -> {
						int b = popInt();
						V128 a = popV128();

						V128.Unary32Function f = switch(viShiftOp) {
							case SHL -> n0 -> n0 << b;
							case SHR_U -> n0 -> n0 >>> b;
							case SHR_S -> n0 -> n0 >> b;
						};

						push(a.unary32(f));
					}

					case VectorInstr.VIBinOp viBinOp -> {
						V128 b = popV128();
						V128 a = popV128();

						V128.Binary32Function f = switch(viBinOp) {
							case ADD -> Integer::sum;
							case SUB -> (n0, n1) -> n0 - n1;
						};

						push(a.binary32(b, f));
					}

					case VectorInstr.VIMinMaxOp viBinOp -> {
						V128 b = popV128();
						V128 a = popV128();

						V128.Binary32Function f = switch(viBinOp) {
							case MIN_U -> Util::minU32;
							case MIN_S -> Math::min;
							case MAX_U -> Util::maxU32;
							case MAX_S -> Math::max;
						};

						push(a.binary32(b, f));
					}

					case VectorInstr.VIMulOp viMulOp -> {
						V128 b = popV128();
						V128 a = popV128();

						V128.Binary32Function f = switch(viMulOp) {
							case MUL -> (n0, n1) -> n0 * n1;
						};

						push(a.binary32(b, f));
					}

					case VectorInstr.I32x4_ExtMul_Low_I16x8_U() -> {
						V128 b = popV128();
						V128 a = popV128();
						V128 result = V128.build32(i -> Short.toUnsignedInt(a.extractLane16(i)) * Short.toUnsignedInt(b.extractLane16(i)));
						push(result);
					}

					case VectorInstr.I32x4_ExtMul_Low_I16x8_S() -> {
						V128 b = popV128();
						V128 a = popV128();
						V128 result = V128.build32(i -> a.extractLane16(i) * b.extractLane16(i));
						push(result);
					}

					case VectorInstr.I32x4_ExtMul_High_I16x8_U() -> {
						V128 b = popV128();
						V128 a = popV128();
						V128 result = V128.build32(i -> Short.toUnsignedInt(a.extractLane16(i + 4)) * Short.toUnsignedInt(b.extractLane16(i + 4)));
						push(result);
					}

					case VectorInstr.I32x4_ExtMul_High_I16x8_S() -> {
						V128 b = popV128();
						V128 a = popV128();
						V128 result = V128.build32(i -> a.extractLane16(i + 4) * b.extractLane16(i + 4));
						push(result);
					}

					case VectorInstr.I32x4_ExtAdd_Pairwise_I16x8_U() -> {
						V128 a = popV128();
						V128 result = V128.build32(i -> Short.toUnsignedInt(a.extractLane16(i)) + Short.toUnsignedInt(a.extractLane16(i + 4)));
						push(result);
					}

					case VectorInstr.I32x4_ExtAdd_Pairwise_I16x8_S() -> {
						V128 a = popV128();
						V128 result = V128.build32(i -> a.extractLane16(i) + a.extractLane16(i + 4));
						push(result);
					}

					case VectorInstr.I32x4_Trunc_Sat_F32x4_U() -> {
						V128 a = popV128();
						V128 result = V128.build32(i -> Util.truncSatF32U32(a.extractLaneF32(i)));
						push(result);
					}

					case VectorInstr.I32x4_Trunc_Sat_F32x4_S() -> {
						V128 a = popV128();
						V128 result = V128.build32(i -> (int)a.extractLaneF32(i));
						push(result);
					}

					case VectorInstr.I32x4_Trunc_Sat_F64x4_U_Zero() -> {
						V128 a = popV128();
						V128 result = V128.build32(i -> i < 2 ? Util.truncSatF64U32(a.extractLaneF64(i)) : 0);
						push(result);
					}
					case VectorInstr.I32x4_Trunc_Sat_F64x2_S_Zero() -> {
						V128 a = popV128();
						V128 result = V128.build32(i -> i < 2 ? (int)a.extractLaneF64(i) : 0);
						push(result);
					}
				}
			}

			case VectorInstr.I64x2_Op_Instr(var op) -> {
				switch(op) {
					case VectorInstr.Splat() -> {
						long a = popLong();
						push(V128.splat64(a));
					}

					case VectorInstr.ExtractLane(var laneIdx) -> {
						V128 a = popV128();
						long result = a.extractLane64(laneIdx);
						push(result);
					}

					case VectorInstr.ReplaceLane(var laneIdx) -> {
						long b = popLong();
						V128 a = popV128();
						V128 result = a.replaceLane64(laneIdx, b);
						push(result);
					}

					case VectorInstr.VIRelOp_S viRelOpS -> {
						V128 b = popV128();
						V128 a = popV128();

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
						V128 a = popV128();

						V128.Unary64Function f = switch(viUnOp) {
							case ABS -> Math::abs;
							case NEG -> n0 -> -n0;
						};

						push(a.unary64(f));
					}

					case VectorInstr.All_True() -> {
						V128 a = popV128();
						boolean result = a.allTrue64();
						push(result ? 1 : 0);
					}

					case VectorInstr.BitMask() -> {
						V128 a = popV128();
						int result = a.bitmask64();
						push(result);
					}

					case VectorInstr.I64x2_Extend_Low_I32x4_U() -> {
						V128 a = popV128();
						V128 result = V128.build64(i -> Integer.toUnsignedLong(a.extractLane32(i)));
						push(result);
					}

					case VectorInstr.I64x2_Extend_Low_I32x4_S() -> {
						V128 a = popV128();
						V128 result = V128.build64(i -> (long)a.extractLane32(i));
						push(result);
					}

					case VectorInstr.I64x2_Extend_High_I32x4_U() -> {
						V128 a = popV128();
						V128 result = V128.build64(i -> Integer.toUnsignedLong(a.extractLane32(i + 2)));
						push(result);
					}

					case VectorInstr.I64x2_Extend_High_I32x4_S() -> {
						V128 a = popV128();
						V128 result = V128.build64(i -> (long)a.extractLane32(i + 2));
						push(result);
					}

					case VectorInstr.VIShiftOp viShiftOp -> {
						int b = popInt();
						V128 a = popV128();

						V128.Unary64Function f = switch(viShiftOp) {
							case SHL -> n0 -> n0 << b;
							case SHR_U -> n0 -> n0 >>> b;
							case SHR_S -> n0 -> n0 >> b;
						};

						push(a.unary64(f));
					}

					case VectorInstr.VIBinOp viBinOp -> {
						V128 b = popV128();
						V128 a = popV128();

						V128.Binary64Function f = switch(viBinOp) {
							case ADD -> Long::sum;
							case SUB -> (n0, n1) -> n0 - n1;
						};

						push(a.binary64(b, f));
					}

					case VectorInstr.VIMulOp viMulOp -> {
						V128 b = popV128();
						V128 a = popV128();

						V128.Binary64Function f = switch(viMulOp) {
							case MUL -> (n0, n1) -> n0 * n1;
						};

						push(a.binary64(b, f));
					}

					case VectorInstr.I64x2_ExtMul_Low_I32x4_U() -> {
						V128 b = popV128();
						V128 a = popV128();

						V128 result = V128.build64(i -> Integer.toUnsignedLong(a.extractLane32(i)) * Integer.toUnsignedLong(b.extractLane32(i)));

						push(result);
					}

					case VectorInstr.I64x2_ExtMul_Low_I32x4_S() -> {
						V128 b = popV128();
						V128 a = popV128();

						V128 result = V128.build64(i -> (long)a.extractLane32(i) * (long)b.extractLane32(i));

						push(result);
					}

					case VectorInstr.I64x2_ExtMul_High_I32x4_U() -> {
						V128 b = popV128();
						V128 a = popV128();

						V128 result = V128.build64(i -> Integer.toUnsignedLong(a.extractLane32(i + 2)) * Integer.toUnsignedLong(b.extractLane32(i + 2)));

						push(result);
					}

					case VectorInstr.I64x2_ExtMul_High_I32x4_S() -> {
						V128 b = popV128();
						V128 a = popV128();

						V128 result = V128.build64(i -> (long)a.extractLane32(i + 2) * (long)b.extractLane32(i + 2));

						push(result);
					}
				}
			}

			case VectorInstr.F32x4_Op_Instr(var op) -> {
				switch(op) {
					case VectorInstr.Splat() -> {
						float a = popFloat();
						push(V128.splatF32(a));
					}

					case VectorInstr.ExtractLane(var laneIdx) -> {
						V128 a = popV128();
						float result = a.extractLaneF32(laneIdx);
						push(result);
					}

					case VectorInstr.ReplaceLane(var laneIdx) -> {
						float b = popFloat();
						V128 a = popV128();
						V128 result = a.replaceLaneF32(laneIdx, b);
						push(result);
					}

					case VectorInstr.VFRelOp vfRelOp -> {
						V128 b = popV128();
						V128 a = popV128();

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
						V128 a = popV128();

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
						V128 b = popV128();
						V128 a = popV128();

						V128.BinaryF32Function f = switch(vfBinOp) {
							case ADD -> Float::sum;
							case SUB -> (n0, n1) -> n0 - n1;
							case MUL -> (n0, n1) -> n0 * n1;
							case DIV -> (n0, n1) -> n0 / n1;
							case MIN -> Util::min;
							case MAX -> Util::max;
							case PMIN -> (n0, n1) -> n1 < n0 ? n1 : n0;
							case PMAX -> (n0, n1) -> n0 < n1 ? n1 : n0;
						};

						push(a.binaryF32(b, f));
					}

					case VectorInstr.F32x4_Convert_I32x4_U() -> {
						V128 a = popV128();
						V128 result = V128.buildF32(i -> (float)Integer.toUnsignedLong(a.extractLane32(i)));
						push(result);
					}

					case VectorInstr.F32x4_Convert_I32x4_S() -> {
						V128 a = popV128();
						V128 result = V128.buildF32(i -> (float)a.extractLane32(i));
						push(result);
					}

					case VectorInstr.F32x4_Demote_F64x2_Zero() -> {
						V128 a = popV128();
						V128 result = V128.buildF32(i -> i < 2 ? (float)a.extractLaneF64(i) : 0.0f);
						push(result);
					}
				}
			}

			case VectorInstr.F32x4_Ternary_Op_Instr(var op) -> {
				switch(op) {
					case VectorInstr.Relaxed_F32x4_MAdd() -> {
						V128 c = popV128();
						V128 b = popV128();
						V128 a = popV128();
						push(a.ternaryF32(b, c, (ai, bi, ci) -> ai * bi + ci));
					}

					case VectorInstr.Relaxed_F32x4_NMAdd() -> {
						V128 c = popV128();
						V128 b = popV128();
						V128 a = popV128();
						push(a.ternaryF32(b, c, (ai, bi, ci) -> -(ai * bi) + ci));
					}
				}
			}

			case VectorInstr.F64x2_Op_Instr(var op) -> {
				switch(op) {
					case VectorInstr.Splat() -> {
						double a = popDouble();
						push(V128.splatF64(a));
					}

					case VectorInstr.ExtractLane(var laneIdx) -> {
						V128 a = popV128();
						double result = a.extractLaneF64(laneIdx);
						push(result);
					}

					case VectorInstr.ReplaceLane(var laneIdx) -> {
						double b = popDouble();
						V128 a = popV128();
						V128 result = a.replaceLaneF64(laneIdx, b);
						push(result);
					}

					case VectorInstr.VFRelOp vfRelOp -> {
						V128 b = popV128();
						V128 a = popV128();

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
						V128 a = popV128();

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
						V128 b = popV128();
						V128 a = popV128();

						V128.BinaryF64Function f = switch(vfBinOp) {
							case ADD -> Double::sum;
							case SUB -> (n0, n1) -> n0 - n1;
							case MUL -> (n0, n1) -> n0 * n1;
							case DIV -> (n0, n1) -> n0 / n1;
							case MIN -> Util::min;
							case MAX -> Util::max;
							case PMIN -> (n0, n1) -> n1 < n0 ? n1 : n0;
							case PMAX -> (n0, n1) -> n0 < n1 ? n1 : n0;
						};

						push(a.binaryF64(b, f));
					}

					case VectorInstr.F64x2_Convert_Low_I32x4_U() -> {
						V128 a = popV128();
						V128 result = V128.buildF64(i -> (double)Integer.toUnsignedLong(a.extractLane32(i)));
						push(result);
					}

					case VectorInstr.F64x2_Convert_Low_I32x4_S() -> {
						V128 a = popV128();
						V128 result = V128.buildF64(i -> (double)a.extractLane32(i));
						push(result);
					}

					case VectorInstr.F64x2_Promote_Low_F32x4() -> {
						V128 a = popV128();
						V128 result = V128.buildF64(i -> (double)a.extractLaneF32(i));
						push(result);
					}
				}
			}

			case VectorInstr.F64x2_Ternary_Op_Instr(var op) -> {
				switch(op) {
					case VectorInstr.Relaxed_F64x2_MAdd() -> {
						V128 c = popV128();
						V128 b = popV128();
						V128 a = popV128();
						push(a.ternaryF64(b, c, (ai, bi, ci) -> ai * bi + ci));
					}

					case VectorInstr.Relaxed_F64x2_NMAdd() -> {
						V128 c = popV128();
						V128 b = popV128();
						V128 a = popV128();
						push(a.ternaryF64(b, c, (ai, bi, ci) -> -(ai * bi) + ci));
					}
				}
			}

			case VectorInstr.I16x8_Relaxed_Dot_I8x16_I7x16_S() -> {
				short[] intermediate = new short[16];
				V128 b = popV128();
				V128 a = popV128();

				for(int i = 0; i < intermediate.length; ++i) {
					intermediate[i] = (short)(a.extractLane8(i) * b.extractLane8(i));
				}
				push(V128.build16(i -> Util.addSatS16(intermediate[2 * i], intermediate[2 * i + 1])));
			}

			case VectorInstr.I32x4_Relaxed_Dot_I8x16_I7x16_Add_S() -> {
				int[] intermediate = new int[16];
				V128 c = popV128();
				V128 b = popV128();
				V128 a = popV128();
				for(int i = 0; i < intermediate.length; ++i) {
					intermediate[i] = a.extractLane8(i) * b.extractLane8(i);
				}

				int[] tmp = new int[8];
				for(int i = 0; i < tmp.length; ++i) {
					tmp[i] = intermediate[2 * i] + intermediate[2 * i + 1];
				}

				push(V128.build32(i -> tmp[2 * i] + tmp[2 * i + 1] + c.extractLane32(i)));
			}
		}
	}

	private void evaluateReferenceInstr(ReferenceInstr instr) throws Throwable {
		switch(instr) {
			case ReferenceInstr.Ref_Func refFunc -> {
				var func = module.getFunction(refFunc.func());
				push(func);
			}
			case ReferenceInstr.Ref_IsNull _ -> {
				Object o = pop();
				int result = o == null ? 1 : 0;
				push(result);
			}

			case ReferenceInstr.Ref_Null _ -> {
				push(null);
			}

			case ReferenceInstr.Ref_Eq() -> {
				var a = pop();
				var b = pop();

				if(a instanceof I31 ia && b instanceof I31 ib) {
					push(ia.signedValue() == ib.signedValue() ? 1 : 0);
					return;
				}

				push(a == b ? 1 : 0);
			}

			case ReferenceInstr.Ref_AsNonNull() -> {
				Object o = pop();
				if(o == null) {
					throw new NullPointerException();
				}
				push(o);
			}

			case ReferenceInstr.Ref_Test(var typeIdx) -> {
				var t = module.closure.resolveRefType(typeIdx);
				var o = pop();
				push(refIsInstance(t, o) ? 1 : 0);
			}

			case ReferenceInstr.Ref_Cast(var typeIdx) -> {
				var t = module.closure.resolveRefType(typeIdx);
				var o = pop();

				if(!refIsInstance(t, o)) {
					throw new WebAssemblyCastTrap();
				}

				push(o);
			}

			case ReferenceInstr.Ref_I31() -> {
				var a = popInt();
				push(new I31(a));
			}

			case ReferenceInstr.I31_Get_S() -> {
				var a = (I31)popNonNull();
				push(a.signedValue());
			}

			case ReferenceInstr.I31_Get_U() -> {
				var a = (I31)popNonNull();
				push(a.unsignedValue());
			}

			case ReferenceInstr.StructInstr structInstr -> evaluateStructInstr(structInstr);
			case ReferenceInstr.ArrayInstr arrayInstr -> evaluateArrayInstr(arrayInstr);

			case ReferenceInstr.Any_Convert_Extern(), ReferenceInstr.Extern_Convert_Any() -> {}

			default -> throw new RuntimeException("Not implemented: " + instr);
		}
	}

	private void evaluateStructInstr(ReferenceInstr.StructInstr instr) throws Throwable {
		switch(instr) {
			case ReferenceInstr.Struct_New(var typeIdx) -> {
				var structType = module.getStructType(typeIdx);
				var fieldTypes = structType.fields();

				@Nullable Object[] values = new Object[fieldTypes.size()];
				for(int i = fieldTypes.size() - 1; i >= 0; --i) {
					values[i] = packValue(pop(), fieldTypes.get(i).storageType());
				}

				push(new DynamicWasmStruct(module.getDefType(typeIdx), values));
			}

			case ReferenceInstr.Struct_New_Default(var typeIdx) -> {
				var structType = module.getStructType(typeIdx);
				var fieldTypes = structType.fields();

				@Nullable Object[] values = new Object[fieldTypes.size()];
				for(int i = fieldTypes.size() - 1; i >= 0; --i) {
					values[i] = Defaults.defaultValuePacked(fieldTypes.get(i).storageType());
				}

				push(new DynamicWasmStruct(module.getDefType(typeIdx), values));
			}

			case ReferenceInstr.Struct_Get(var typeIdx, var fieldIdx) -> {
				var structType = module.getStructType(typeIdx);
				var fieldType = structType.fields().get(fieldIdx.index());

				var o = (DynamicWasmStruct)popNonNull();

				switch(fieldType.storageType()) {
					case PackedType _ -> throw new RuntimeException("Expected a val type");
					case ValType _ -> push(o.getField(fieldIdx.index()));
				}
			}

			case ReferenceInstr.Struct_Get_S(var typeIdx, var fieldIdx) -> {
				var structType = module.getStructType(typeIdx);
				var fieldType = structType.fields().get(fieldIdx.index());

				var o = (DynamicWasmStruct)popNonNull();

				push(unpackValueS(o.getField(fieldIdx.index()), fieldType.storageType()));
			}

			case ReferenceInstr.Struct_Get_U(var typeIdx, var fieldIdx) -> {
				var structType = module.getStructType(typeIdx);
				var fieldType = structType.fields().get(fieldIdx.index());

				var o = (DynamicWasmStruct)popNonNull();

				push(unpackValueU(o.getField(fieldIdx.index()), fieldType.storageType()));
			}

			case ReferenceInstr.Struct_Set(var typeIdx, var fieldIdx) -> {
				var structType = module.getStructType(typeIdx);
				var fieldType = structType.fields().get(fieldIdx.index());

				var value = pop();
				var o = (DynamicWasmStruct)popNonNull();

				o.setField(fieldIdx.index(), packValue(value, fieldType.storageType()));
			}
		}
	}

	private void evaluateArrayInstr(ReferenceInstr.ArrayInstr instr) throws Throwable {
		switch(instr) {
			case ReferenceInstr.Array_New(var typeIdx) -> {
				var arrayType = module.getArrayType(typeIdx);

				int n = popInt();
				var val = packValue(pop(), arrayType.fieldType().storageType());

				var arr = DynamicWasmArray.create(module.getDefType(typeIdx), n);
				for(int i = 0; i < n; ++i) {
					arr.set(i, val);
				}

				push(arr);
			}

			case ReferenceInstr.Array_New_Default(var typeIdx) -> {
				var arrayType = module.getArrayType(typeIdx);

				int n = popInt();
				var val = Defaults.defaultValuePacked(arrayType.fieldType().storageType());

				var arr = DynamicWasmArray.create(module.getDefType(typeIdx), n);
				for(int i = 0; i < n; ++i) {
					arr.set(i, val);
				}

				push(arr);
			}

			case ReferenceInstr.Array_New_Fixed(var typeIdx, int n) -> {
				var arrayType = module.getArrayType(typeIdx);

				var arr = DynamicWasmArray.create(module.getDefType(typeIdx), n);
				for(int i = n - 1; i >= 0; --i) {
					var val = packValue(pop(), arrayType.fieldType().storageType());
					arr.set(i, val);
				}

				push(arr);
			}

			case ReferenceInstr.Array_New_Data(var typeIdx, var dataIdx) -> {
				var arrayType = module.getArrayType(typeIdx);
				var data = module.getData(dataIdx);

				int n = popInt();
				int s = popInt();
				int d = 0;
				var array = DynamicWasmArray.create(module.getDefType(typeIdx), n);


				switch(arrayType.fieldType().storageType()) {
					case PackedType packedType -> {
						switch(packedType) {
							case I8 -> copyDataToArrayByte((DynamicWasmArray.OfByte)array, data.init(), d, s, n);
							case I16 -> copyDataToArrayShort((DynamicWasmArray.OfShort)array, data.init(), d, s, n);
						}
					}
					case NumType numType -> {
						switch(numType) {
							case I32 -> copyDataToArrayInt((DynamicWasmArray.OfInt)array, data.init(), d, s, n);
							case I64 -> copyDataToArrayLong((DynamicWasmArray.OfLong)array, data.init(), d, s, n);
							case F32 -> copyDataToArrayFloat((DynamicWasmArray.OfFloat)array, data.init(), d, s, n);
							case F64 -> copyDataToArrayDouble((DynamicWasmArray.OfDouble)array, data.init(), d, s, n);
						}
					}
					case VecType vecType -> {
						switch(vecType) {
							case V128 -> copyDataToArrayV128(array, data.init(), d, s, n);
						}
					}
					default -> throw new RuntimeException("Reference type array cannot be created from data");
				}

				push(array);
			}

			case ReferenceInstr.Array_New_Elem(var typeIdx, var elemIdx) -> {
				var arrayType = module.getArrayType(typeIdx);
				var elem = module.getElement(elemIdx);

				int n = popInt();
				int s = popInt();
				int d = 0;
				var array = DynamicWasmArray.create(module.getDefType(typeIdx), n);

				Objects.checkFromIndexSize(s, n, elem.size());
				Objects.checkFromIndexSize(d, n, array.length());

				for(int i = 0; i < n; ++i) {
					var value = packValue(elem.get(s + i), arrayType.fieldType().storageType());
					array.set(d + i, value);
				}

				push(array);
			}

			case ReferenceInstr.Array_Get _ -> {
				int i = popInt();
				var a = (DynamicWasmArray)popNonNull();

				push(a.get(i));
			}

			case ReferenceInstr.Array_Get_S(var typeIdx) -> {
				var arrayType = module.getArrayType(typeIdx);

				int i = popInt();
				var a = (DynamicWasmArray)popNonNull();

				push(unpackValueS(a.get(i), arrayType.fieldType().storageType()));
			}

			case ReferenceInstr.Array_Get_U(var typeIdx) -> {
				var arrayType = module.getArrayType(typeIdx);

				int i = popInt();
				var a = (DynamicWasmArray)popNonNull();

				push(unpackValueU(a.get(i), arrayType.fieldType().storageType()));
			}

			case ReferenceInstr.Array_Set(var typeIdx) -> {
				var arrayType = module.getArrayType(typeIdx);

				var val = pop();
				int i = popInt();
				var a = (DynamicWasmArray)popNonNull();

				a.set(i, packValue(val, arrayType.fieldType().storageType()));
			}

			case ReferenceInstr.Array_Len() -> {
				var a = (DynamicWasmArray)popNonNull();
				push(a.length());
			}

			case ReferenceInstr.Array_Fill(var typeIdx) -> {
				var arrayType = module.getArrayType(typeIdx);

				int n = popInt();
				Object val = packValue(pop(), arrayType.fieldType().storageType());
				int d = popInt();
				var array = (DynamicWasmArray)pop();

				if(array == null) {
					throw new NullPointerException();
				}

				Objects.checkFromIndexSize(d, n, array.length());

				for(int i = 0; i < n; ++i) {
					array.set(d + i, val);
				}
			}

			case ReferenceInstr.Array_Copy _ -> {
				int n = popInt();
				int s = popInt();
				var src = (DynamicWasmArray)pop();
				int d = popInt();
				var dest = (DynamicWasmArray)pop();

				if(src == null || dest == null) {
					throw new NullPointerException();
				}

				Objects.checkFromIndexSize(s, n, src.length());
				Objects.checkFromIndexSize(d, n, dest.length());

				if(d <= s) {
					for(int i = 0; i < n; ++i) {
						dest.set(d + i, src.get(s + i));
					}
				}
				else {
					for(int i = n - 1; i >= 0; --i) {
						dest.set(d + i, src.get(s + i));
					}
				}
			}

			case ReferenceInstr.Array_Init_Data(var typeIdx, var dataIdx) -> {
				var arrayType = module.getArrayType(typeIdx);
				var data = module.getData(dataIdx);

				int n = popInt();
				int s = popInt();
				int d = popInt();
				var array = (DynamicWasmArray)pop();

				if(array == null) {
					throw new NullPointerException();
				}

				switch(arrayType.fieldType().storageType()) {
					case PackedType packedType -> {
						switch(packedType) {
							case I8 -> copyDataToArrayByte((DynamicWasmArray.OfByte)array, data.init(), d, s, n);
							case I16 -> copyDataToArrayShort((DynamicWasmArray.OfShort)array, data.init(), d, s, n);
						}
					}
					case NumType numType -> {
						switch(numType) {
							case I32 -> copyDataToArrayInt((DynamicWasmArray.OfInt)array, data.init(), d, s, n);
							case I64 -> copyDataToArrayLong((DynamicWasmArray.OfLong)array, data.init(), d, s, n);
							case F32 -> copyDataToArrayFloat((DynamicWasmArray.OfFloat)array, data.init(), d, s, n);
							case F64 -> copyDataToArrayDouble((DynamicWasmArray.OfDouble)array, data.init(), d, s, n);
						}
					}
					case VecType vecType -> {
						switch(vecType) {
							case V128 -> copyDataToArrayV128(array, data.init(), d, s, n);
						}
					}
					default -> throw new RuntimeException("Reference type array cannot be created from data");
				}
			}

			case ReferenceInstr.Array_Init_Elem(var typeIdx, var elemIdx) -> {
				var arrayType = module.getArrayType(typeIdx);
				var elem = module.getElement(elemIdx);

				int n = popInt();
				int s = popInt();
				int d = popInt();
				var array = (DynamicWasmArray)pop();

				if(array == null) {
					throw new NullPointerException();
				}

				Objects.checkFromIndexSize(s, n, elem.size());
				Objects.checkFromIndexSize(d, n, array.length());

				for(int i = 0; i < n; ++i) {
					array.set(d + i, packValue(elem.get(s + i), arrayType.fieldType().storageType()));
				}
			}
		}
	}

	private @Nullable Object packValue(@Nullable Object value, StorageType t) {
		return switch(t) {
			case PackedType packedType -> {
				Objects.requireNonNull(value);
				yield switch(packedType) {
					case I8 -> (byte)(int)value;
					case I16 -> (short)(int)value;
				};
			}
			case ValType _ -> value;
		};
	}

	private Object unpackValueS(@Nullable Object value, StorageType t) {
		return switch(t) {
			case PackedType packedType -> {
				Objects.requireNonNull(value);
				yield switch(packedType) {
					case I8 -> (int)(byte)value;
					case I16 -> (int)(short)value;
				};
			}
			case ValType _ -> throw new RuntimeException("Expected a packed type");
		};
	}

	private Object unpackValueU(@Nullable Object value, StorageType t) {
		return switch(t) {
			case PackedType packedType -> {
				Objects.requireNonNull(value);
				yield switch(packedType) {
					case I8 -> Byte.toUnsignedInt((byte)value);
					case I16 -> Short.toUnsignedInt((short)value);
				};
			}
			case ValType _ -> throw new RuntimeException("Expected a packed type");
		};
	}

	private boolean refIsInstance(RefType t, @Nullable Object o) {
		if(o == null) {
			return t.isNullable();
		}

		HeapType objType;
		if(module.subtyping.isSubtypeHeap(t.heapType(), HeapType.AbstractHeapType.EXTERN)) {
			objType = HeapType.AbstractHeapType.EXTERN;
		}
		else {
			if(o instanceof DynamicWasmObject wo) {
				objType = wo.heapType();
			}
			else if(o instanceof I31) {
				objType = HeapType.AbstractHeapType.I31;
			}
			else {
				objType = HeapType.AbstractHeapType.ANY;
			}
		}


		return module.subtyping.isSubtypeHeap(objType, t.heapType());
	}

	private void copyDataToArrayByte(DynamicWasmArray.OfByte dest, ByteString src, int d, int s, int n) {
		Objects.checkFromIndexSize(s, n, src.size());
		Objects.checkFromIndexSize(d, n, dest.length());

		for(int i = 0; i < n; ++i) {
			dest.setByte(d + i, src.byteAt(s + i));
		}
	}

	private void copyDataToArrayShort(DynamicWasmArray.OfShort dest, ByteString src, int d, int s, int n) {
		Objects.checkFromIndexSize(s, n, src.size());
		Objects.checkFromIndexSize(d, n, dest.length());

		for(int i = 0; i < n; ++i) {
			short value = 0;
			for(int j = 0; j < 2; ++j) {
				value |= (short)((src.byteAt(s + i * 2 + j) & 0xFF) << (8 * j));
			}
			dest.setShort(d + i, value);
		}
	}

	private void copyDataToArrayInt(DynamicWasmArray.OfInt dest, ByteString src, int d, int s, int n) {
		Objects.checkFromIndexSize(s, (long)n * 4, src.size());
		Objects.checkFromIndexSize(d, n, dest.length());

		for(int i = 0; i < n; ++i) {
			int value = 0;
			for(int j = 0; j < 4; ++j) {
				value |= (src.byteAt(s + i * 4 + j) & 0xFF) << (8 * j);
			}

			dest.setInt(d + i, value);
		}
	}

	private void copyDataToArrayLong(DynamicWasmArray.OfLong dest, ByteString src, int d, int s, int n) {
		Objects.checkFromIndexSize(s, (long)n * 8, src.size());
		Objects.checkFromIndexSize(d, n, dest.length());

		for(int i = 0; i < n; ++i) {
			long value = 0;
			for(int j = 0; j < 8; ++j) {
				value |= (long)(src.byteAt(s + i * 8 + j) & 0xFF) << (8 * j);
			}

			dest.setLong(d + i, value);
		}
	}

	private void copyDataToArrayFloat(DynamicWasmArray.OfFloat dest, ByteString src, int d, int s, int n) {
		Objects.checkFromIndexSize(s, (long)n * 4, src.size());
		Objects.checkFromIndexSize(d, n, dest.length());

		for(int i = 0; i < n; ++i) {
			int value = 0;
			for(int j = 0; j < 4; ++j) {
				value |= (src.byteAt(s + i * 4 + j) & 0xFF) << (8 * j);
			}

			dest.setFloat(d + i, Float.intBitsToFloat(value));
		}
	}

	private void copyDataToArrayDouble(DynamicWasmArray.OfDouble dest, ByteString src, int d, int s, int n) {
		Objects.checkFromIndexSize(s, (long)n * 8, src.size());
		Objects.checkFromIndexSize(d, n, dest.length());

		for(int i = 0; i < n; ++i) {
			long value = 0;
			for(int j = 0; j < 8; ++j) {
				value |= (long)(src.byteAt(s + i * 8 + j) & 0xFF) << (8 * j);
			}

			dest.setDouble(d + i, Double.longBitsToDouble(value));
		}
	}

	private void copyDataToArrayV128(DynamicWasmArray dest, ByteString src, int d, int s, int n) {
		Objects.checkFromIndexSize(s, (long)n * 16, src.size());
		Objects.checkFromIndexSize(d, n, dest.length());

		for(int i = 0; i < n; ++i) {
			final int i2 = i;
			V128 value = V128.build8(j -> src.byteAt(s + i2 * 16 + j));
			dest.set(d + i, value);
		}
	}

	private void evaluateParametricInstruction(ParametricInstr instr) throws Throwable {
		switch(instr) {
			case ParametricInstr.Drop() -> {
				pop();
			}
			case ParametricInstr.Select _ -> {
				int c = popInt();
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
			case TableInstr.Table_Get(var tableIdx) -> {
				var table = module.getTable(tableIdx);
				long i = popIndex(table);
				Object val = table.get(i);
				push(val);
			}

			case TableInstr.Table_Set(var tableIdx) -> {
				var table = module.getTable(tableIdx);
				Object val = pop();
				long i = popIndex(table);
				table.set(i, val);
			}

			case TableInstr.Table_Size(var tableIdx) -> {
				var table = module.getTable(tableIdx);
				long size = table.size();
				pushIndex(table, size);
			}

			case TableInstr.Table_Grow(var tableIdx) -> {
				var table = module.getTable(tableIdx);
				long n = popIndex(table);
				Object val = pop();
				long result = table.grow(n, val);
				pushIndex(table, result);
			}

			case TableInstr.Table_Fill(var tableIdx) -> {
				var table = module.getTable(tableIdx);

				long n = popIndex(table);
				Object val = pop();
				long i = popIndex(table);

				WasmTable.fill(n, val, i, table);
			}

			case TableInstr.Table_Copy(var dest, var src) -> {
				WasmTable tableX = module.getTable(dest);
				WasmTable tableY = module.getTable(src);

				long n = popIndex(tableX, tableY);
				long s = popIndex(tableY);
				long d = popIndex(tableX);

				WasmTable.copy(n, s, d, tableX, tableY);
			}

			case TableInstr.Table_Init(var tableIdx, var elemIdx) -> {
				WasmTable table = module.getTable(tableIdx);
				WasmElements elem = module.getElement(elemIdx);

				int n = popInt();
				int s = popInt();
				long d = popIndex(table);

				WasmTable.init(d, s, n, table, elem);
			}

			case TableInstr.Elem_Drop(var elemIdx) -> {
				module.dropElement(elemIdx);
			}
		}
	}

	private long popIndex(WasmTable table) {
		return AddressTypeUtils.unboxAddress(table.type().addrType(), pop());
	}

	private long popIndex(WasmTable table1, WasmTable table2) {
		return switch(table1.type().addrType()) {
			case I32 -> popIndex(table1);
			case I64 -> popIndex(table2);
		};
	}

	private void pushIndex(WasmTable table, long index) {
		push(AddressTypeUtils.boxAddress(table.type().addrType(), index));
	}

	private void evaluateMemoryInstruction(MemoryInstr instr) throws Throwable {
		switch(instr) {
			case MemoryInstr.Inn_Load innLoad -> {
				var memory = module.getMemory(innLoad.memArg().memIdx());
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, innLoad.memArg().offset());

				switch(innLoad.numSize()) {
					case _32 -> push(memory.loadI32(address));
					case _64 -> push(memory.loadI64(address));
				}
			}

			case MemoryInstr.Fnn_Load fnnLoad -> {
				var memory = module.getMemory(fnnLoad.memArg().memIdx());
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, fnnLoad.memArg().offset());

				switch(fnnLoad.numSize()) {
					case _32 -> push(memory.loadF32(address));
					case _64 -> push(memory.loadF64(address));
				}
			}
			
			case MemoryInstr.Inn_Store innStore -> {
				var memory = module.getMemory(innStore.memArg().memIdx());
				Object value = popNonNull();
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, innStore.memArg().offset());

				switch(innStore.numSize()) {
					case _32 -> memory.storeI32(address, (int)value);
					case _64 -> memory.storeI64(address, (long)value);
				}
			}

			case MemoryInstr.Fnn_Store fnnStore -> {
				var memory = module.getMemory(fnnStore.memArg().memIdx());
				Object value = popNonNull();
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, fnnStore.memArg().offset());

				switch(fnnStore.numSize()) {
					case _32 -> memory.storeF32(address, (float)value);
					case _64 -> memory.storeF64(address, (double)value);
				}
			}

			case MemoryInstr.V128_Load v128Load -> {
				var memory = module.getMemory(v128Load.memArg().memIdx());
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, v128Load.memArg().offset());
				push(memory.loadV128(address));
			}

			case MemoryInstr.V128_Store v128Store -> {
				var memory = module.getMemory(v128Store.memArg().memIdx());
				V128 value = popV128();
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, v128Store.memArg().offset());

				memory.storeV128(address, value);
			}

			case MemoryInstr.Inn_Load8_U innLoad8U -> {
				var memory = module.getMemory(innLoad8U.memArg().memIdx());
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, innLoad8U.memArg().offset());
				byte value = memory.loadI8(address);

				switch(innLoad8U.numSize()) {
					case _32 -> push(Byte.toUnsignedInt(value));
					case _64 -> push(Byte.toUnsignedLong(value));
				}
			}

			case MemoryInstr.Inn_Load8_S innLoad8S -> {
				var memory = module.getMemory(innLoad8S.memArg().memIdx());
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, innLoad8S.memArg().offset());
				byte value = memory.loadI8(address);

				switch(innLoad8S.numSize()) {
					case _32 -> push((int)value);
					case _64 -> push((long)value);
				}
			}

			case MemoryInstr.Inn_Load16_U innLoad16U -> {
				var memory = module.getMemory(innLoad16U.memArg().memIdx());
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, innLoad16U.memArg().offset());
				short value = memory.loadI16(address);

				switch(innLoad16U.numSize()) {
					case _32 -> push(Short.toUnsignedInt(value));
					case _64 -> push(Short.toUnsignedLong(value));
				}
			}

			case MemoryInstr.Inn_Load16_S innLoad16S -> {
				var memory = module.getMemory(innLoad16S.memArg().memIdx());
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, innLoad16S.memArg().offset());
				short value = memory.loadI16(address);

				switch(innLoad16S.numSize()) {
					case _32 -> push((int)value);
					case _64 -> push((long)value);
				}
			}

			case MemoryInstr.I64_Load32_U(var memArg) -> {
				var memory = module.getMemory(memArg.memIdx());
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, memArg.offset());
				int value = memory.loadI32(address);
				push(Integer.toUnsignedLong(value));
			}

			case MemoryInstr.I64_Load32_S(var memArg) -> {
				var memory = module.getMemory(memArg.memIdx());
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, memArg.offset());
				int value = memory.loadI32(address);
				push((long)value);
			}

			case MemoryInstr.Inn_Store8 innStore8 -> {
				var memory = module.getMemory(innStore8.memArg().memIdx());
				Object value = popNonNull();
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, innStore8.memArg().offset());

				byte numValue = switch(innStore8.numSize()) {
					case _32 -> (byte)(int)value;
					case _64 -> (byte)(long)value;
				};

				memory.storeI8(address, numValue);
			}

			case MemoryInstr.Inn_Store16 innStore16 -> {
				var memory = module.getMemory(innStore16.memArg().memIdx());
				Object value = popNonNull();
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, innStore16.memArg().offset());

				short numValue = switch(innStore16.numSize()) {
					case _32 -> (short)(int)value;
					case _64 -> (short)(long)value;
				};

				memory.storeI16(address, numValue);
			}

			case MemoryInstr.I64_Store32(var memArg) -> {
				var memory = module.getMemory(memArg.memIdx());
				int value = (int)popLong();
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, memArg.offset());

				memory.storeI32(address, value);
			}



			case MemoryInstr.V128_Load8x8_U(var memArg) -> {
				var memory = module.getMemory(memArg.memIdx());
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, memArg.offset());

				byte[] values = new byte[8];
				for(int j = 0; j < values.length; ++j) {
					values[j] = memory.loadI8(calculateMemoryAddress(address, j));
				}

				V128 result = V128.build16(j -> (short)Byte.toUnsignedInt(values[j]));
				push(result);
			}

			case MemoryInstr.V128_Load8x8_S(var memArg) -> {
				var memory = module.getMemory(memArg.memIdx());
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, memArg.offset());

				byte[] values = new byte[8];
				for(int j = 0; j < values.length; ++j) {
					values[j] = memory.loadI8(calculateMemoryAddress(address, j));
				}

				V128 result = V128.build16(j -> values[j]);
				push(result);
			}

			case MemoryInstr.V128_Load16x4_U(var memArg) -> {
				var memory = module.getMemory(memArg.memIdx());
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, memArg.offset());

				short[] values = new short[4];
				values[0] = memory.loadI16(calculateMemoryAddress(address, 0));
				values[1] = memory.loadI16(calculateMemoryAddress(address, 2));
				values[2] = memory.loadI16(calculateMemoryAddress(address, 4));
				values[3] = memory.loadI16(calculateMemoryAddress(address, 6));

				V128 result = V128.build32(j -> Short.toUnsignedInt(values[j]));
				push(result);
			}

			case MemoryInstr.V128_Load16x4_S(var memArg) -> {
				var memory = module.getMemory(memArg.memIdx());
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, memArg.offset());

				short[] values = new short[4];
				values[0] = memory.loadI16(calculateMemoryAddress(address, 0));
				values[1] = memory.loadI16(calculateMemoryAddress(address, 2));
				values[2] = memory.loadI16(calculateMemoryAddress(address, 4));
				values[3] = memory.loadI16(calculateMemoryAddress(address, 6));

				V128 result = V128.build32(j -> values[j]);
				push(result);
			}

			case MemoryInstr.V128_Load32x2_U(var memArg) -> {
				var memory = module.getMemory(memArg.memIdx());
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, memArg.offset());

				int[] values = new int[2];
				values[0] = memory.loadI32(calculateMemoryAddress(address, 0));
				values[1] = memory.loadI32(calculateMemoryAddress(address, 4));

				V128 result = V128.build64(j -> Integer.toUnsignedLong(values[j]));
				push(result);
			}

			case MemoryInstr.V128_Load32x2_S(var memArg) -> {
				var memory = module.getMemory(memArg.memIdx());
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, memArg.offset());

				int[] values = new int[4];
				values[0] = memory.loadI32(calculateMemoryAddress(address, 0));
				values[1] = memory.loadI32(calculateMemoryAddress(address, 4));

				V128 result = V128.build64(j -> values[j]);
				push(result);
			}

			case MemoryInstr.V128_Load32_Zero(var memArg) -> {
				var memory = module.getMemory(memArg.memIdx());
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, memArg.offset());

				int value = memory.loadI32(address);
				V128 result = V128.build32(j -> j == 0 ? value : 0);
				push(result);
			}

			case MemoryInstr.V128_Load64_Zero(var memArg) -> {
				var memory = module.getMemory(memArg.memIdx());
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, memArg.offset());

				long value = memory.loadI64(address);
				V128 result = V128.build64(j -> j == 0 ? value : 0);
				push(result);
			}

			case MemoryInstr.V128_Load8_Splat(var memArg) -> {
				var memory = module.getMemory(memArg.memIdx());
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, memArg.offset());
				byte value = memory.loadI8(address);
				V128 result = V128.splat8(value);
				push(result);
			}

			case MemoryInstr.V128_Load16_Splat(var memArg) -> {
				var memory = module.getMemory(memArg.memIdx());
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, memArg.offset());
				short value = memory.loadI16(address);
				V128 result = V128.splat16(value);
				push(result);
			}

			case MemoryInstr.V128_Load32_Splat(var memArg) -> {
				var memory = module.getMemory(memArg.memIdx());
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, memArg.offset());
				int value = memory.loadI32(address);
				V128 result = V128.splat32(value);
				push(result);
			}

			case MemoryInstr.V128_Load64_Splat(var memArg) -> {
				var memory = module.getMemory(memArg.memIdx());
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, memArg.offset());
				long value = memory.loadI64(address);
				V128 result = V128.splat64(value);
				push(result);
			}

			case MemoryInstr.V128_Load8_Lane(var memArg, var laneIdx) -> {
				var memory = module.getMemory(memArg.memIdx());
				V128 v = popV128();
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, memArg.offset());
				byte value = memory.loadI8(address);
				V128 result = v.replaceLane8(laneIdx, value);
				push(result);
			}

			case MemoryInstr.V128_Load16_Lane(var memArg, var laneIdx) -> {
				var memory = module.getMemory(memArg.memIdx());
				V128 v = popV128();
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, memArg.offset());
				short value = memory.loadI16(address);
				V128 result = v.replaceLane16(laneIdx, value);
				push(result);
			}

			case MemoryInstr.V128_Load32_Lane(var memArg, var laneIdx) -> {
				var memory = module.getMemory(memArg.memIdx());
				V128 v = popV128();
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, memArg.offset());
				int value = memory.loadI32(address);
				V128 result = v.replaceLane32(laneIdx, value);
				push(result);
			}

			case MemoryInstr.V128_Load64_Lane(var memArg, var laneIdx) -> {
				var memory = module.getMemory(memArg.memIdx());
				V128 v = popV128();
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, memArg.offset());
				long value = memory.loadI64(address);
				V128 result = v.replaceLane64(laneIdx, value);
				push(result);
			}

			case MemoryInstr.V128_Store8_Lane(var memArg, var laneIdx) -> {
				var memory = module.getMemory(memArg.memIdx());
				V128 v = popV128();
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, memArg.offset());
				memory.storeI8(address, v.extractLane8(laneIdx));
			}

			case MemoryInstr.V128_Store16_Lane(var memArg, var laneIdx) -> {
				var memory = module.getMemory(memArg.memIdx());
				V128 v = popV128();
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, memArg.offset());
				memory.storeI16(address, v.extractLane16(laneIdx));
			}
			case MemoryInstr.V128_Store32_Lane(var memArg, var laneIdx) -> {
				var memory = module.getMemory(memArg.memIdx());
				V128 v = popV128();
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, memArg.offset());
				memory.storeI32(address, v.extractLane32(laneIdx));
			}
			case MemoryInstr.V128_Store64_Lane(var memArg, var laneIdx) -> {
				var memory = module.getMemory(memArg.memIdx());
				V128 v = popV128();
				long i = popAddress(memory);
				long address = calculateMemoryAddress(i, memArg.offset());
				memory.storeI64(address, v.extractLane64(laneIdx));
			}

			case MemoryInstr.Memory_Size(var memIdx) -> {
				var memory = module.getMemory(memIdx);
				long size = memory.pageSize();
				pushAddress(memory, size);
			}

			case MemoryInstr.Memory_Grow(var memIdx) -> {
				var memory = module.getMemory(memIdx);
				long n = popAddress(memory);
				long growRes = memory.grow(n);
				pushAddress(memory, growRes);
			}

			case MemoryInstr.Memory_Fill(var memIdx) -> {
				var memory = module.getMemory(memIdx);
				long n = popAddress(memory);
				byte val = (byte)popInt();
				long d = popAddress(memory);

				if(!Util.sumInRange(d, n, memory.byteSize())) {
					throw new IndexOutOfBoundsException();
				}

				while(n != 0) {
					memory.storeI8(d, val);
					++d;
					--n;
				}
			}

			case MemoryInstr.Memory_Copy(var dstMemIdx, var srcMemIdx) -> {
				var dstMemory = module.getMemory(dstMemIdx);
				var srcMemory = module.getMemory(srcMemIdx);
				long n = popAddress(dstMemory, srcMemory);
				long s = popAddress(srcMemory);
				long d = popAddress(dstMemory);

				if(!Util.sumInRange(d, n, dstMemory.byteSize()) || !Util.sumInRange(s, n, srcMemory.byteSize())) {
					throw new IndexOutOfBoundsException();
				}

				if(d <= s) {
					while(n != 0) {
						byte b = srcMemory.loadI8(s);
						dstMemory.storeI8(d, b);
						++d;
						++s;
						--n;
					}
				}
				else {
					while(n != 0) {
						byte b = srcMemory.loadI8(s + n - 1);
						dstMemory.storeI8(d + n - 1, b);
						--n;
					}
				}
			}

			case MemoryInstr.Memory_Init(var memIdx, var dataIdx) -> {
				var memory = module.getMemory(memIdx);
				var data = module.getData(dataIdx);
				int n = popInt();
				int s = popInt();
				long d = popAddress(memory);

				memory.copyFromArray(d, s, n, data.init().toByteArray());
			}


			case MemoryInstr.Data_Drop(var dataIdx) -> module.dropData(dataIdx);
		}
	}

	private long popAddress(WasmMemory memory) {
		return AddressTypeUtils.unboxAddress(memory.addressType(), pop());
	}

	private long popAddress(WasmMemory memory1, WasmMemory memory2) {
		return switch(memory1.addressType()) {
			case I32 -> popAddress(memory1);
			case I64 -> popAddress(memory2);
		};
	}

	private void pushAddress(WasmMemory memory, long address) {
		push(AddressTypeUtils.boxAddress(memory.addressType(), address));
	}

	private long calculateMemoryAddress(long address, long offset) {
		if(
			(
				offset < 0 && address < offset
			) ||
				(
					Long.compareUnsigned(address + offset, address) < 0 ||
						Long.compareUnsigned(address + offset, offset) < 0
				)
		) {
			throw new IndexOutOfBoundsException();
		}

		return address + offset;
	}

	private @Nullable DynamicFunctionResult evaluateControlInstruction(ControlInstr instr) throws Throwable {
		return switch(instr) {
			case ControlInstr.Nop() -> null;
			case ControlInstr.Unreachable() -> throw new UnreachableTrap();
			case ControlInstr.Block(var type, var innerBlock) -> {
				enterBlock(type, innerBlock, ip + 1, true);
				yield null;
			}
			case ControlInstr.Loop(var type, var innerBlock) -> {
				enterBlock(type, innerBlock, ip, false);
				yield null;
			}
			case ControlInstr.If(var type, var thenBlock, var elseBlock) -> {
				int value = popInt();
				var innerBlock = value != 0 ? thenBlock : elseBlock;
				enterBlock(type, innerBlock, ip + 1, true);
				yield null;
			}
			case ControlInstr.Throw(var tag) -> {
				var wasmTag = module.getTag(tag);
				var values = getTopValues(wasmTag.funcType().args().types().size());
				throw new DynamicWebAssemblyException(wasmTag, values);
			}
			case ControlInstr.Throw_Ref() -> {
				throw (DynamicWebAssemblyException)popNonNull();
			}
			case ControlInstr.Br(var label) -> {
				branch(label.index());
				yield null;
			}
			case ControlInstr.Br_If(var label) -> {
				int value = popInt();
				if(value != 0) {
					branch(label.index());
				}
				yield null;
			}
			case ControlInstr.Br_Table(var table, var fallback) -> {
				int value = popInt();

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
			case ControlInstr.Br_OnNull(var label) -> {
				Object o = pop();
				if(o == null) {
					branch(label.index());
				}
				else {
					push(o);
				}
				yield null;
			}
			case ControlInstr.Br_OnNonNull(var label) -> {
				Object o = pop();
				if(o != null) {
					push(o);
					branch(label.index());
				}
				yield null;
			}
			case ControlInstr.Br_OnCast(var label, var _, var t2) -> {
				Object o = peek();
				if(refIsInstance(module.closure.resolveRefType(t2), o)) {
					branch(label.index());
				}
				yield null;
			}
			case ControlInstr.Br_OnCastFail(var label, var _, var t2) -> {
				Object o = peek();
				if(!refIsInstance(module.closure.resolveRefType(t2), o)) {
					branch(label.index());
				}
				yield null;
			}
			case ControlInstr.Return() -> {
				var result = getTopValues(topBlockType.results().types().size());
				yield new DynamicFunctionResult.Values(result);
			}
			case ControlInstr.Call(var funcIdx) -> {
				var func = module.getFunction(funcIdx);
				var args = getTopValues(func.functionType().args().types().size());
				@Nullable Object[] results = func.invokeNow(args);
				pushAll(results);
				yield null;
			}
			case ControlInstr.Call_Ref _ -> {
				var func = (DynamicWasmFunction)pop();
				if(func == null) {
					throw new NullPointerException();
				}

				var args = getTopValues(func.functionType().args().types().size());
				@Nullable Object[] results = func.invokeNow(args);
				pushAll(results);
				yield null;
			}
			case ControlInstr.Call_Indirect(var tableIdx, var funcTypeIdx) -> {
				var table = module.getTable(tableIdx);
				long index = popIndex(table);

				var defType = module.getDefType(funcTypeIdx);
				var func = (DynamicWasmFunction)table.get(index);
				Objects.requireNonNull(func);
				var funcObjType = func.type();

				if(!module.subtyping.isSubtypeDefType(funcObjType, defType)) {
					throw new IndirectCallTypeMismatchTrap("Expected: " + defType + ", Actual: " + funcObjType);
				}

				var args = getTopValues(func.functionType().args().types().size());
				@Nullable Object[] results = func.invokeNow(args);
				pushAll(results);
				yield null;
			}
			case ControlInstr.Return_Call(var funcIdx) -> {
				var func = module.getFunction(funcIdx);
				var args = getTopValues(func.functionType().args().types().size());
				yield (DynamicFunctionResult.Delay)() -> func.invoke(args);
			}
			case ControlInstr.Return_Call_Ref _ -> {
				var func = (DynamicWasmFunction)pop();
				if(func == null) {
					throw new NullPointerException();
				}

				var args = getTopValues(func.functionType().args().types().size());
				yield (DynamicFunctionResult.Delay)() -> func.invoke(args);
			}
			case ControlInstr.Return_Call_Indirect(var tableIdx, var funcTypeIdx) -> {
				var table = module.getTable(tableIdx);
				long index = popIndex(table);

				var defType = module.getDefType(funcTypeIdx);
				var func = (DynamicWasmFunction)table.get(index);
				Objects.requireNonNull(func);

				if(!module.subtyping.isSubtypeDefType(func.type(), defType)) {
					throw new IndirectCallTypeMismatchTrap();
				}

				var args = getTopValues(func.functionType().args().types().size());
				yield (DynamicFunctionResult.Delay)() -> func.invoke(args);
			}
			case ControlInstr.Try_Table(var blockType, var catchClauses, var innerBlock) -> {
				enterBlock(blockType, innerBlock, new ExceptionHandler(catchClauses), ip + 1, true);
				yield null;
			}
		};
	}


	private void handleException(DynamicWebAssemblyException ex) throws DynamicWebAssemblyException {
		while(!this.stack.isEmpty()) {
			if(!(pop() instanceof ExceptionHandler(var catchClauses))) {
				continue;
			}

			for(var catchClause : catchClauses) {
				switch(catchClause) {
					case ControlInstr.CatchTag(var tagIdx, var labelIdx) -> {
						var tag = module.getTag(tagIdx);
						if(ex.getTag() == tag) {
							pushAll(ex.getValues());
							branch(labelIdx.index());
							return;
						}
					}
					case ControlInstr.CatchTagRef(var tagIdx, var labelIdx) -> {
						var tag = module.getTag(tagIdx);
						if(ex.getTag() == tag) {
							pushAll(ex.getValues());
							push(ex);
							branch(labelIdx.index());
							return;
						}
					}
					case ControlInstr.CatchAll(var labelIdx) -> {
						branch(labelIdx.index());
						return;
					}
					case ControlInstr.CatchAllRef(var labelIdx) -> {
						push(ex);
						branch(labelIdx.index());
						return;
					}
				}
			}
		}

		throw ex;
	}



	private FuncType expandBlockType(ControlInstr.BlockType blockType) {
		return switch(blockType) {
			case ControlInstr.BlockType.Empty() -> new FuncType(new ResultType(ImmutableList.of()), new ResultType(ImmutableList.of()));
			case ControlInstr.BlockType.OfIndex(var index) -> module.getFuncType(index);
			case ControlInstr.BlockType.OfValType(var valType) -> new FuncType(new ResultType(ImmutableList.of()), new ResultType(ImmutableList.of(valType)));
		};
	}

	private void enterBlock(ControlInstr.BlockType type, List<? extends Instr> innerBlock, int branchIP, boolean useResultType) {
		enterBlock(type, innerBlock, null, branchIP, useResultType);
	}

	private void enterBlock(ControlInstr.BlockType type, List<? extends Instr> innerBlock, @Nullable ExceptionHandler handler, int branchIP, boolean useResultType) {
		var expandedType = expandBlockType(type);
		var label = new Label(block, blockType, useResultType ? expandedType.results() : expandedType.args(), branchIP, ip + 1);

		@Nullable Object[] values = getTopValues(expandedType.args().types().size());

		if(handler != null) push(handler);
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
		@Nullable Object[] values = getTopValues(label.resultType().types().size());

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
