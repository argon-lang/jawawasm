package jvmwasm.format.binary;

import jvmwasm.format.ModuleFormatException;
import jvmwasm.format.data.V128;
import jvmwasm.format.instructions.*;
import jvmwasm.format.modules.*;
import jvmwasm.format.modules.Module;
import jvmwasm.format.types.*;
import org.jspecify.annotations.Nullable;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class ModuleReader {
	public ModuleReader(InputStream is, long moduleLength) {
		this.is = is;
		this.moduleLength = moduleLength;
	}

	private final InputStream is;
	private final long moduleLength;
	private long offset = 0;
	private boolean hasDataCount = false;
	private long sectionStart = -1;
	private long sectionSize;

	private byte readByte() throws IOException, ModuleFormatException {
		int b = is.read();
		if(b >= 0) {
			++offset;
			return (byte)b;
		}
		else {
			throw new ModuleFormatException("unexpected end of section or function");
		}
	}

	private byte[] readAllNBytes(int n) throws IOException, ModuleFormatException {
		byte[] buff = is.readNBytes(n);
		offset += buff.length;
		if(buff.length < n) {
			throw new ModuleFormatException("unexpected end of section or function");
		}
		return buff;
	}

	private int readFixedInt() throws IOException, ModuleFormatException {
		byte[] buff = readAllNBytes(4);
		return ByteBuffer.wrap(buff).order(ByteOrder.LITTLE_ENDIAN).getInt(0);
	}

	private static interface ValueReader<T> {
		T read() throws IOException, ModuleFormatException;
	}

	private <T> List<? extends T> readVector(ValueReader<T> reader) throws IOException, ModuleFormatException {
		int len = readU32();
		List<T> l = new ArrayList<>(len);
		for(int i = 0; i < len; ++i) {
			l.add(reader.read());
		}
		return l;
	}
	
	private byte[] readByteVec() throws IOException, ModuleFormatException {
		int len = readU32();
		return readAllNBytes(len);
	}

	public int readU7() throws IOException, ModuleFormatException {
		byte b = readByte();
		if((b & 0x80) == 0x80) {
			throw new ModuleFormatException("integer representation too long");
		}

		return b;
	}

	public int readS7() throws IOException, ModuleFormatException {
		byte b = readByte();
		if((b & 0x80) == 0x80) {
			throw new ModuleFormatException("integer representation too long");
		}

		if((b & 0x40) == 0x40) {
			b |= 0x80;
		}

		return b;
	}

	public int readU32() throws IOException, ModuleFormatException {
		byte b;
		int value = 0;
		int shift = 0;


		do {
			b = readByte();

			if(shift == 28) {
				if((b & 0x80) == 0x80) {
					throw new ModuleFormatException("integer representation too long");
				}
				else if((b & 0x70) != 0) {
					throw new ModuleFormatException("integer too large");
				}
			}

			value |= (b & 0x7F) << shift;
			shift += 7;

		} while((b & 0x80) == 0x80);
		return value;
	}

	public int readS32() throws IOException, ModuleFormatException {
		byte b;
		int value = 0;
		int shift = 0;

		do {
			b = readByte();

			if(shift == 28) {
				if((b & 0x80) == 0x80) {
					throw new ModuleFormatException("integer representation too long");
				}
				else if((b & 0x78) != 0 && (b & 0x78) != 0x78) {
					throw new ModuleFormatException("integer too large");
				}
			}

			value |= (b & 0x7F) << shift;
			shift += 7;

		} while((b & 0x80) == 0x80);

		if(shift < 32 && (b & 0x40) == 0x40) {
			value |= ~0 << shift;
		}

		return value;
	}

	public long readS64() throws IOException, ModuleFormatException {
		byte b;
		long value = 0;
		int shift = 0;

		do {
			b = readByte();

			if(shift == 63) {
				if((b & 0x80) == 0x80) {
					throw new ModuleFormatException("integer representation too long");
				}
				else if((b & 0x7F) != 0 && (b & 0x7F) != 0x7F) {
					throw new ModuleFormatException("integer too large");
				}
			}

			value |= (long)(b & 0x7F) << shift;
			shift += 7;

		} while((b & 0x80) == 0x80);

		if(shift < 64 && (b & 0x40) == 0x40) {
			value |= ~0L << shift;
		}

		return value;
	}

	private float readF32() throws IOException, ModuleFormatException {
		byte[] buff = readAllNBytes(4);
		return ByteBuffer.wrap(buff).order(ByteOrder.LITTLE_ENDIAN).getFloat(0);
	}

	private double readF64() throws IOException, ModuleFormatException {
		byte[] buff = readAllNBytes(8);
		return ByteBuffer.wrap(buff).order(ByteOrder.LITTLE_ENDIAN).getDouble(0);
	}

	public String readName() throws IOException, ModuleFormatException {
		byte[] data = readByteVec();
		var decoder = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT);

		var bb = ByteBuffer.wrap(data);
		CharBuffer cb;
		try {
			cb = decoder.decode(bb);
		}
		catch(MalformedInputException | UnmappableCharacterException ex) {
			throw new ModuleFormatException("malformed UTF-8 encoding", ex);
		}

		return cb.toString();
	}


	private ValType intToValType(int i) throws ModuleFormatException {
		return switch(i) {
			case -1 -> NumType.I32;
			case -2 -> NumType.I64;
			case -3 -> NumType.F32;
			case -4 -> NumType.F64;
			case -5 -> VecType.V128;
			case -16 -> new FuncRef();
			case -17 -> new ExternRef();
			default -> throw new ModuleFormatException("Unexpected value type: " + Integer.toHexString(i));
		};
	}

	public ValType readValType() throws IOException, ModuleFormatException {
		int i = readS7();
		return intToValType(i);
	}

	public RefType readRefType() throws IOException, ModuleFormatException {
		var t = readValType();
		if(t instanceof RefType refType) {
			return refType;
		}
		else {
			throw new ModuleFormatException("malformed reference type");
		}
	}

	public ResultType readResultType() throws IOException, ModuleFormatException {
		var types = readVector(this::readValType);
		return new ResultType(types);
	}

	public FuncType readFuncType() throws IOException, ModuleFormatException {
		int i = readS7();
		if(i != -32) {
			throw new ModuleFormatException("invalid function type");
		}

		var from = readResultType();
		var to = readResultType();

		return new FuncType(from, to);
	}

	public Limits readLimits() throws IOException, ModuleFormatException {
		int b = readU7();
		return switch(b) {
			case 0x00 -> {
				int min = readU32();
				yield new Limits(min, null);
			}

			case 0x01 -> {
				int min = readU32();
				int max = readU32();
				yield new Limits(min, max);
			}

			default -> throw new ModuleFormatException("integer too large");
		};
	}

	public MemType readMemType() throws IOException, ModuleFormatException {
		var limits = readLimits();
		return new MemType(limits);
	}

	public TableType readTableType() throws IOException, ModuleFormatException {
		var elementType = readRefType();
		var limits = readLimits();
		return new TableType(limits, elementType);
	}

	public GlobalType readGlobalType() throws IOException, ModuleFormatException {
		var t = readValType();
		var mut = switch(readByte()) {
			case 0x00 -> Mut.Const;
			case 0x01 -> Mut.Var;
			default -> throw new ModuleFormatException("malformed mutability");
		};

		return new GlobalType(mut, t);
	}



	public ControlInstr.BlockType readBlockType() throws IOException, ModuleFormatException {
		int value = readS32();

		if(value == -64) {
			return new ControlInstr.BlockType.Empty();
		}
		else if(value < 0) {
			return new ControlInstr.BlockType.OfValType(intToValType(value));
		}
		else {
			return new ControlInstr.BlockType.OfIndex(new TypeIdx(value));
		}
	}

	public InstrOrTerminator readInstrOrTerminator() throws IOException, ModuleFormatException {
		byte b = readByte();

		return switch(Byte.toUnsignedInt(b)) {
			// Control
			case 0x00 -> new ControlInstr.Unreachable();
			case 0x01 -> new ControlInstr.Nop();
			case 0x02 -> {
				var bt = readBlockType();

				List<Instr> instrs = new ArrayList<>();
				while(true) {
					var instr = readInstrOrTerminator();
					if(instr == BlockTerminator.END) {
						break;
					}
					else if(instr instanceof Instr i) {
						instrs.add(i);
					}
					else {
						throw new ModuleFormatException("END opcode expected");
					}
				}

				yield new ControlInstr.Block(bt, instrs);
			}

			case 0x03 -> {
				var bt = readBlockType();

				List<Instr> instrs = new ArrayList<>();
				while(true) {
					var instr = readInstrOrTerminator();
					if(instr == BlockTerminator.END) {
						break;
					}
					else if(instr instanceof Instr i) {
						instrs.add(i);
					}
					else {
						throw new ModuleFormatException("END opcode expected");
					}
				}

				yield new ControlInstr.Loop(bt, instrs);
			}

			case 0x04 -> {
				var bt = readBlockType();

				List<Instr> thenInstrs = new ArrayList<>();
				boolean reachedEnd = false;
				boolean hasElse = false;
				while(!reachedEnd && !hasElse) {
					var instr = readInstrOrTerminator();
					switch(instr) {
						case BlockTerminator terminator -> {
							switch(terminator) {
								case END -> reachedEnd = true;
								case ELSE -> hasElse = true;
							}
						}

						case Instr i -> thenInstrs.add(i);
					}
				}

				List<Instr> elseInstrs = new ArrayList<>();
				if(hasElse) {
					reachedEnd = false;
					while(!reachedEnd) {
						var instr = readInstrOrTerminator();
						switch(instr) {
							case BlockTerminator terminator -> {
								switch(terminator) {
									case END -> reachedEnd = true;
									case ELSE -> throw new ModuleFormatException("END opcode expected");
								}
							}

							case Instr i -> elseInstrs.add(i);
						}
					}
				}

				yield new ControlInstr.If(bt, thenInstrs, elseInstrs);
			}

			case 0x05 -> BlockTerminator.ELSE;
			case 0x0B -> BlockTerminator.END;

			case 0x0C -> {
				var idx = readLabelIdx();
				yield new ControlInstr.Br(idx);
			}

			case 0x0D -> {
				var idx = readLabelIdx();
				yield new ControlInstr.Br_If(idx);
			}

			case 0x0E -> {
				var indexes = readVector(this::readLabelIdx);
				var fallback = readLabelIdx();
				yield new ControlInstr.Br_Table(indexes, fallback);
			}

			case 0x0F -> new ControlInstr.Return();

			case 0x10 -> {
				var func = readFuncIdx();
				yield new ControlInstr.Call(func);
			}

			case 0x11 -> {
				var t = readTypeIdx();
				var table = readTableIdx();
				yield new ControlInstr.Call_Indirect(table, t);
			}

			case 0x12 -> {
				var func = readFuncIdx();
				yield new ControlInstr.Return_Call(func);
			}

			case 0x13 -> {
				var t = readTypeIdx();
				var table = readTableIdx();
				yield new ControlInstr.Return_Call_Indirect(table, t);
			}


			// Reference
			case 0xD0 -> {
				var t = readRefType();
				yield new ReferenceInstr.Ref_Null(t);
			}

			case 0xD1 -> new ReferenceInstr.Ref_IsNull();
			case 0xD2 -> {
				var f = readFuncIdx();
				yield new ReferenceInstr.Ref_Func(f);
			}

			// Parametric
			case 0x1A -> new ParametricInstr.Drop();
			case 0x1B -> new ParametricInstr.Select(null);
			case 0x1C -> {
				var t = readVector(this::readValType);
				yield new ParametricInstr.Select(t);
			}

			// Variable
			case 0x20 -> {
				var local = readLocalIdx();
				yield new VariableInstr.Local_Get(local);
			}

			case 0x21 -> {
				var local = readLocalIdx();
				yield new VariableInstr.Local_Set(local);
			}

			case 0x22 -> {
				var local = readLocalIdx();
				yield new VariableInstr.Local_Tee(local);
			}

			case 0x23 -> {
				var global = readGlobalIdx();
				yield new VariableInstr.Global_Get(global);
			}

			case 0x24 -> {
				var global = readGlobalIdx();
				yield new VariableInstr.Global_Set(global);
			}

			// Table
			case 0x25 -> {
				var table = readTableIdx();
				yield new TableInstr.Table_Get(table);
			}

			case 0x26 -> {
				var table = readTableIdx();
				yield new TableInstr.Table_Set(table);
			}

			// Memory
			case 0x28 -> {
				var arg = readMemArg();
				yield new MemoryInstr.Inn_Load(NumericInstr.NumSize._32, arg);
			}

			case 0x29 -> {
				var arg = readMemArg();
				yield new MemoryInstr.Inn_Load(NumericInstr.NumSize._64, arg);
			}

			case 0x2A -> {
				var arg = readMemArg();
				yield new MemoryInstr.Fnn_Load(NumericInstr.NumSize._32, arg);
			}

			case 0x2B -> {
				var arg = readMemArg();
				yield new MemoryInstr.Fnn_Load(NumericInstr.NumSize._64, arg);
			}

			case 0x2C -> {
				var arg = readMemArg();
				yield new MemoryInstr.Inn_Load8_S(NumericInstr.NumSize._32, arg);
			}

			case 0x2D -> {
				var arg = readMemArg();
				yield new MemoryInstr.Inn_Load8_U(NumericInstr.NumSize._32, arg);
			}

			case 0x2E -> {
				var arg = readMemArg();
				yield new MemoryInstr.Inn_Load16_S(NumericInstr.NumSize._32, arg);
			}

			case 0x2F -> {
				var arg = readMemArg();
				yield new MemoryInstr.Inn_Load16_U(NumericInstr.NumSize._32, arg);
			}

			case 0x30 -> {
				var arg = readMemArg();
				yield new MemoryInstr.Inn_Load8_S(NumericInstr.NumSize._64, arg);
			}

			case 0x31 -> {
				var arg = readMemArg();
				yield new MemoryInstr.Inn_Load8_U(NumericInstr.NumSize._64, arg);
			}

			case 0x32 -> {
				var arg = readMemArg();
				yield new MemoryInstr.Inn_Load16_S(NumericInstr.NumSize._64, arg);
			}

			case 0x33 -> {
				var arg = readMemArg();
				yield new MemoryInstr.Inn_Load16_U(NumericInstr.NumSize._64, arg);
			}

			case 0x34 -> {
				var arg = readMemArg();
				yield new MemoryInstr.I64_Load32_S(arg);
			}

			case 0x35 -> {
				var arg = readMemArg();
				yield new MemoryInstr.I64_Load32_U(arg);
			}

			case 0x36 -> {
				var arg = readMemArg();
				yield new MemoryInstr.Inn_Store(NumericInstr.NumSize._32, arg);
			}

			case 0x37 -> {
				var arg = readMemArg();
				yield new MemoryInstr.Inn_Store(NumericInstr.NumSize._64, arg);
			}

			case 0x38 -> {
				var arg = readMemArg();
				yield new MemoryInstr.Fnn_Store(NumericInstr.NumSize._32, arg);
			}

			case 0x39 -> {
				var arg = readMemArg();
				yield new MemoryInstr.Fnn_Store(NumericInstr.NumSize._64, arg);
			}

			case 0x3A -> {
				var arg = readMemArg();
				yield new MemoryInstr.Inn_Store8(NumericInstr.NumSize._32, arg);
			}

			case 0x3C -> {
				var arg = readMemArg();
				yield new MemoryInstr.Inn_Store8(NumericInstr.NumSize._64, arg);
			}

			case 0x3B -> {
				var arg = readMemArg();
				yield new MemoryInstr.Inn_Store16(NumericInstr.NumSize._32, arg);
			}

			case 0x3D -> {
				var arg = readMemArg();
				yield new MemoryInstr.Inn_Store16(NumericInstr.NumSize._64, arg);
			}

			case 0x3E -> {
				var arg = readMemArg();
				yield new MemoryInstr.I64_Store32(arg);
			}

			case 0x3F -> {
				var index = readByte();
				if(index != 0) {
					throw new ModuleFormatException("zero byte expected");
				}

				yield new MemoryInstr.Memory_Size();
			}

			case 0x40 -> {
				var index = readByte();
				if(index != 0) {
					throw new ModuleFormatException("zero byte expected");
				}

				yield new MemoryInstr.Memory_Grow();
			}

			// Numeric
			case 0x41 -> {
				var n = readS32();
				yield new NumericInstr.I32_Const(n);
			}


			case 0x42 -> {
				var n = readS64();
				yield new NumericInstr.I64_Const(n);
			}


			case 0x43 -> {
				var x = readF32();
				yield new NumericInstr.F32_Const(x);
			}


			case 0x44 -> {
				var x = readF64();
				yield new NumericInstr.F64_Const(x);
			}

			case 0x45 -> new NumericInstr.Inn_ITestOp(NumericInstr.NumSize._32, NumericInstr.ITestOp.EQZ);
			case 0x46 -> new NumericInstr.Inn_IRelOp(NumericInstr.NumSize._32, NumericInstr.IRelOp.EQ);
			case 0x47 -> new NumericInstr.Inn_IRelOp(NumericInstr.NumSize._32, NumericInstr.IRelOp.NE);
			case 0x48 -> new NumericInstr.Inn_IRelOp(NumericInstr.NumSize._32, NumericInstr.IRelOp.LT_S);
			case 0x49 -> new NumericInstr.Inn_IRelOp(NumericInstr.NumSize._32, NumericInstr.IRelOp.LT_U);
			case 0x4A -> new NumericInstr.Inn_IRelOp(NumericInstr.NumSize._32, NumericInstr.IRelOp.GT_S);
			case 0x4B -> new NumericInstr.Inn_IRelOp(NumericInstr.NumSize._32, NumericInstr.IRelOp.GT_U);
			case 0x4C -> new NumericInstr.Inn_IRelOp(NumericInstr.NumSize._32, NumericInstr.IRelOp.LE_S);
			case 0x4D -> new NumericInstr.Inn_IRelOp(NumericInstr.NumSize._32, NumericInstr.IRelOp.LE_U);
			case 0x4E -> new NumericInstr.Inn_IRelOp(NumericInstr.NumSize._32, NumericInstr.IRelOp.GE_S);
			case 0x4F -> new NumericInstr.Inn_IRelOp(NumericInstr.NumSize._32, NumericInstr.IRelOp.GE_U);

			case 0x50 -> new NumericInstr.Inn_ITestOp(NumericInstr.NumSize._64, NumericInstr.ITestOp.EQZ);
			case 0x51 -> new NumericInstr.Inn_IRelOp(NumericInstr.NumSize._64, NumericInstr.IRelOp.EQ);
			case 0x52 -> new NumericInstr.Inn_IRelOp(NumericInstr.NumSize._64, NumericInstr.IRelOp.NE);
			case 0x53 -> new NumericInstr.Inn_IRelOp(NumericInstr.NumSize._64, NumericInstr.IRelOp.LT_S);
			case 0x54 -> new NumericInstr.Inn_IRelOp(NumericInstr.NumSize._64, NumericInstr.IRelOp.LT_U);
			case 0x55 -> new NumericInstr.Inn_IRelOp(NumericInstr.NumSize._64, NumericInstr.IRelOp.GT_S);
			case 0x56 -> new NumericInstr.Inn_IRelOp(NumericInstr.NumSize._64, NumericInstr.IRelOp.GT_U);
			case 0x57 -> new NumericInstr.Inn_IRelOp(NumericInstr.NumSize._64, NumericInstr.IRelOp.LE_S);
			case 0x58 -> new NumericInstr.Inn_IRelOp(NumericInstr.NumSize._64, NumericInstr.IRelOp.LE_U);
			case 0x59 -> new NumericInstr.Inn_IRelOp(NumericInstr.NumSize._64, NumericInstr.IRelOp.GE_S);
			case 0x5A -> new NumericInstr.Inn_IRelOp(NumericInstr.NumSize._64, NumericInstr.IRelOp.GE_U);

			case 0x5B -> new NumericInstr.Fnn_FRelOp(NumericInstr.NumSize._32, NumericInstr.FRelOp.EQ);
			case 0x5C -> new NumericInstr.Fnn_FRelOp(NumericInstr.NumSize._32, NumericInstr.FRelOp.NE);
			case 0x5D -> new NumericInstr.Fnn_FRelOp(NumericInstr.NumSize._32, NumericInstr.FRelOp.LT);
			case 0x5E -> new NumericInstr.Fnn_FRelOp(NumericInstr.NumSize._32, NumericInstr.FRelOp.GT);
			case 0x5F -> new NumericInstr.Fnn_FRelOp(NumericInstr.NumSize._32, NumericInstr.FRelOp.LE);
			case 0x60 -> new NumericInstr.Fnn_FRelOp(NumericInstr.NumSize._32, NumericInstr.FRelOp.GE);

			case 0x61 -> new NumericInstr.Fnn_FRelOp(NumericInstr.NumSize._64, NumericInstr.FRelOp.EQ);
			case 0x62 -> new NumericInstr.Fnn_FRelOp(NumericInstr.NumSize._64, NumericInstr.FRelOp.NE);
			case 0x63 -> new NumericInstr.Fnn_FRelOp(NumericInstr.NumSize._64, NumericInstr.FRelOp.LT);
			case 0x64 -> new NumericInstr.Fnn_FRelOp(NumericInstr.NumSize._64, NumericInstr.FRelOp.GT);
			case 0x65 -> new NumericInstr.Fnn_FRelOp(NumericInstr.NumSize._64, NumericInstr.FRelOp.LE);
			case 0x66 -> new NumericInstr.Fnn_FRelOp(NumericInstr.NumSize._64, NumericInstr.FRelOp.GE);
			
			case 0x67 -> new NumericInstr.Inn_IUnOp(NumericInstr.NumSize._32, NumericInstr.IUnOp.CLZ);
			case 0x68 -> new NumericInstr.Inn_IUnOp(NumericInstr.NumSize._32, NumericInstr.IUnOp.CTZ);
			case 0x69 -> new NumericInstr.Inn_IUnOp(NumericInstr.NumSize._32, NumericInstr.IUnOp.POPCNT);
			case 0x6A -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._32, NumericInstr.IBinOp.ADD);
			case 0x6B -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._32, NumericInstr.IBinOp.SUB);
			case 0x6C -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._32, NumericInstr.IBinOp.MUL);
			case 0x6D -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._32, NumericInstr.IBinOp.DIV_S);
			case 0x6E -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._32, NumericInstr.IBinOp.DIV_U);
			case 0x6F -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._32, NumericInstr.IBinOp.REM_S);
			case 0x70 -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._32, NumericInstr.IBinOp.REM_U);
			case 0x71 -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._32, NumericInstr.IBinOp.AND);
			case 0x72 -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._32, NumericInstr.IBinOp.OR);
			case 0x73 -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._32, NumericInstr.IBinOp.XOR);
			case 0x74 -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._32, NumericInstr.IBinOp.SHL);
			case 0x75 -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._32, NumericInstr.IBinOp.SHR_S);
			case 0x76 -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._32, NumericInstr.IBinOp.SHR_U);
			case 0x77 -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._32, NumericInstr.IBinOp.ROTL);
			case 0x78 -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._32, NumericInstr.IBinOp.ROTR);

			case 0x79 -> new NumericInstr.Inn_IUnOp(NumericInstr.NumSize._64, NumericInstr.IUnOp.CLZ);
			case 0x7A -> new NumericInstr.Inn_IUnOp(NumericInstr.NumSize._64, NumericInstr.IUnOp.CTZ);
			case 0x7B -> new NumericInstr.Inn_IUnOp(NumericInstr.NumSize._64, NumericInstr.IUnOp.POPCNT);
			case 0x7C -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._64, NumericInstr.IBinOp.ADD);
			case 0x7D -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._64, NumericInstr.IBinOp.SUB);
			case 0x7E -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._64, NumericInstr.IBinOp.MUL);
			case 0x7F -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._64, NumericInstr.IBinOp.DIV_S);
			case 0x80 -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._64, NumericInstr.IBinOp.DIV_U);
			case 0x81 -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._64, NumericInstr.IBinOp.REM_S);
			case 0x82 -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._64, NumericInstr.IBinOp.REM_U);
			case 0x83 -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._64, NumericInstr.IBinOp.AND);
			case 0x84 -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._64, NumericInstr.IBinOp.OR);
			case 0x85 -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._64, NumericInstr.IBinOp.XOR);
			case 0x86 -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._64, NumericInstr.IBinOp.SHL);
			case 0x87 -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._64, NumericInstr.IBinOp.SHR_S);
			case 0x88 -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._64, NumericInstr.IBinOp.SHR_U);
			case 0x89 -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._64, NumericInstr.IBinOp.ROTL);
			case 0x8A -> new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._64, NumericInstr.IBinOp.ROTR);
			
			case 0x8B -> new NumericInstr.Fnn_FUnOp(NumericInstr.NumSize._32, NumericInstr.FUnOp.ABS);
			case 0x8C -> new NumericInstr.Fnn_FUnOp(NumericInstr.NumSize._32, NumericInstr.FUnOp.NEG);
			case 0x8D -> new NumericInstr.Fnn_FUnOp(NumericInstr.NumSize._32, NumericInstr.FUnOp.CEIL);
			case 0x8E -> new NumericInstr.Fnn_FUnOp(NumericInstr.NumSize._32, NumericInstr.FUnOp.FLOOR);
			case 0x8F -> new NumericInstr.Fnn_FUnOp(NumericInstr.NumSize._32, NumericInstr.FUnOp.TRUNC);
			case 0x90 -> new NumericInstr.Fnn_FUnOp(NumericInstr.NumSize._32, NumericInstr.FUnOp.NEAREST);
			case 0x91 -> new NumericInstr.Fnn_FUnOp(NumericInstr.NumSize._32, NumericInstr.FUnOp.SQRT);
			case 0x92 -> new NumericInstr.Fnn_FBinOp(NumericInstr.NumSize._32, NumericInstr.FBinOp.ADD);
			case 0x93 -> new NumericInstr.Fnn_FBinOp(NumericInstr.NumSize._32, NumericInstr.FBinOp.SUB);
			case 0x94 -> new NumericInstr.Fnn_FBinOp(NumericInstr.NumSize._32, NumericInstr.FBinOp.MUL);
			case 0x95 -> new NumericInstr.Fnn_FBinOp(NumericInstr.NumSize._32, NumericInstr.FBinOp.DIV);
			case 0x96 -> new NumericInstr.Fnn_FBinOp(NumericInstr.NumSize._32, NumericInstr.FBinOp.MIN);
			case 0x97 -> new NumericInstr.Fnn_FBinOp(NumericInstr.NumSize._32, NumericInstr.FBinOp.MAX);
			case 0x98 -> new NumericInstr.Fnn_FBinOp(NumericInstr.NumSize._32, NumericInstr.FBinOp.COPYSIGN);

			case 0x99 -> new NumericInstr.Fnn_FUnOp(NumericInstr.NumSize._64, NumericInstr.FUnOp.ABS);
			case 0x9A -> new NumericInstr.Fnn_FUnOp(NumericInstr.NumSize._64, NumericInstr.FUnOp.NEG);
			case 0x9B -> new NumericInstr.Fnn_FUnOp(NumericInstr.NumSize._64, NumericInstr.FUnOp.CEIL);
			case 0x9C -> new NumericInstr.Fnn_FUnOp(NumericInstr.NumSize._64, NumericInstr.FUnOp.FLOOR);
			case 0x9D -> new NumericInstr.Fnn_FUnOp(NumericInstr.NumSize._64, NumericInstr.FUnOp.TRUNC);
			case 0x9E -> new NumericInstr.Fnn_FUnOp(NumericInstr.NumSize._64, NumericInstr.FUnOp.NEAREST);
			case 0x9F -> new NumericInstr.Fnn_FUnOp(NumericInstr.NumSize._64, NumericInstr.FUnOp.SQRT);
			case 0xA0 -> new NumericInstr.Fnn_FBinOp(NumericInstr.NumSize._64, NumericInstr.FBinOp.ADD);
			case 0xA1 -> new NumericInstr.Fnn_FBinOp(NumericInstr.NumSize._64, NumericInstr.FBinOp.SUB);
			case 0xA2 -> new NumericInstr.Fnn_FBinOp(NumericInstr.NumSize._64, NumericInstr.FBinOp.MUL);
			case 0xA3 -> new NumericInstr.Fnn_FBinOp(NumericInstr.NumSize._64, NumericInstr.FBinOp.DIV);
			case 0xA4 -> new NumericInstr.Fnn_FBinOp(NumericInstr.NumSize._64, NumericInstr.FBinOp.MIN);
			case 0xA5 -> new NumericInstr.Fnn_FBinOp(NumericInstr.NumSize._64, NumericInstr.FBinOp.MAX);
			case 0xA6 -> new NumericInstr.Fnn_FBinOp(NumericInstr.NumSize._64, NumericInstr.FBinOp.COPYSIGN);

			case 0xA7 -> new NumericInstr.I32_Wrap_I64();
			case 0xA8 -> new NumericInstr.Inn_Trunc_Fmm_S(NumericInstr.NumSize._32, NumericInstr.NumSize._32);
			case 0xA9 -> new NumericInstr.Inn_Trunc_Fmm_U(NumericInstr.NumSize._32, NumericInstr.NumSize._32);
			case 0xAA -> new NumericInstr.Inn_Trunc_Fmm_S(NumericInstr.NumSize._32, NumericInstr.NumSize._64);
			case 0xAB -> new NumericInstr.Inn_Trunc_Fmm_U(NumericInstr.NumSize._32, NumericInstr.NumSize._64);
			case 0xAC -> new NumericInstr.I64_Extend_I32_S();
			case 0xAD -> new NumericInstr.I64_Extend_I32_U();
			case 0xAE -> new NumericInstr.Inn_Trunc_Fmm_S(NumericInstr.NumSize._64, NumericInstr.NumSize._32);
			case 0xAF -> new NumericInstr.Inn_Trunc_Fmm_U(NumericInstr.NumSize._64, NumericInstr.NumSize._32);
			case 0xB0 -> new NumericInstr.Inn_Trunc_Fmm_S(NumericInstr.NumSize._64, NumericInstr.NumSize._64);
			case 0xB1 -> new NumericInstr.Inn_Trunc_Fmm_U(NumericInstr.NumSize._64, NumericInstr.NumSize._64);
			case 0xB2 -> new NumericInstr.Fnn_Convert_Imm_S(NumericInstr.NumSize._32, NumericInstr.NumSize._32);
			case 0xB3 -> new NumericInstr.Fnn_Convert_Imm_U(NumericInstr.NumSize._32, NumericInstr.NumSize._32);
			case 0xB4 -> new NumericInstr.Fnn_Convert_Imm_S(NumericInstr.NumSize._32, NumericInstr.NumSize._64);
			case 0xB5 -> new NumericInstr.Fnn_Convert_Imm_U(NumericInstr.NumSize._32, NumericInstr.NumSize._64);
			case 0xB6 -> new NumericInstr.F32_Demote_F64();
			case 0xB7 -> new NumericInstr.Fnn_Convert_Imm_S(NumericInstr.NumSize._64, NumericInstr.NumSize._32);
			case 0xB8 -> new NumericInstr.Fnn_Convert_Imm_U(NumericInstr.NumSize._64, NumericInstr.NumSize._32);
			case 0xB9 -> new NumericInstr.Fnn_Convert_Imm_S(NumericInstr.NumSize._64, NumericInstr.NumSize._64);
			case 0xBA -> new NumericInstr.Fnn_Convert_Imm_U(NumericInstr.NumSize._64, NumericInstr.NumSize._64);
			case 0xBB -> new NumericInstr.F64_Promote_F32();
			case 0xBC -> new NumericInstr.Inn_Reinterpret_Fnn(NumericInstr.NumSize._32);
			case 0xBD -> new NumericInstr.Inn_Reinterpret_Fnn(NumericInstr.NumSize._64);
			case 0xBE -> new NumericInstr.Fnn_Reinterpret_Inn(NumericInstr.NumSize._32);
			case 0xBF -> new NumericInstr.Fnn_Reinterpret_Inn(NumericInstr.NumSize._64);

			case 0xC0 -> new NumericInstr.Inn_Extend8_S(NumericInstr.NumSize._32);
			case 0xC1 -> new NumericInstr.Inn_Extend16_S(NumericInstr.NumSize._32);
			case 0xC2 -> new NumericInstr.Inn_Extend8_S(NumericInstr.NumSize._64);
			case 0xC3 -> new NumericInstr.Inn_Extend16_S(NumericInstr.NumSize._64);
			case 0xC4 -> new NumericInstr.I64_Extend32_S();


			// Extended Opcodes
			case 0xFC -> switch(readU32()) {
				// Numeric
				case 0 -> new NumericInstr.Inn_Trunc_Sat_Fmm_S(NumericInstr.NumSize._32, NumericInstr.NumSize._32);
				case 1 -> new NumericInstr.Inn_Trunc_Sat_Fmm_U(NumericInstr.NumSize._32, NumericInstr.NumSize._32);
				case 2 -> new NumericInstr.Inn_Trunc_Sat_Fmm_S(NumericInstr.NumSize._32, NumericInstr.NumSize._64);
				case 3 -> new NumericInstr.Inn_Trunc_Sat_Fmm_U(NumericInstr.NumSize._32, NumericInstr.NumSize._64);
				case 4 -> new NumericInstr.Inn_Trunc_Sat_Fmm_S(NumericInstr.NumSize._64, NumericInstr.NumSize._32);
				case 5 -> new NumericInstr.Inn_Trunc_Sat_Fmm_U(NumericInstr.NumSize._64, NumericInstr.NumSize._32);
				case 6 -> new NumericInstr.Inn_Trunc_Sat_Fmm_S(NumericInstr.NumSize._64, NumericInstr.NumSize._64);
				case 7 -> new NumericInstr.Inn_Trunc_Sat_Fmm_U(NumericInstr.NumSize._64, NumericInstr.NumSize._64);

				// Memory
				case 8 -> {
					var data = readDataIdx();
					var index = readByte();
					if(index != 0) {
						throw new ModuleFormatException("expected 0");
					}

					yield new MemoryInstr.Memory_Init(data);
				}

				case 9 -> {
					var data = readDataIdx();
					yield new MemoryInstr.Data_Drop(data);
				}

				case 10 -> {
					var src = readByte();
					if(src != 0) {
						throw new ModuleFormatException("expected 0");
					}

					var dest = readByte();
					if(dest != 0) {
						throw new ModuleFormatException("expected 0");
					}

					yield new MemoryInstr.Memory_Copy();
				}

				case 11 -> {
					var index = readByte();
					if(index != 0) {
						throw new ModuleFormatException("expected 0");
					}

					yield new MemoryInstr.Memory_Fill();
				}

				// Table
				case 12 -> {
					var elem = readElemIdx();
					var table = readTableIdx();
					yield new TableInstr.Table_Init(table, elem);
				}

				case 13 -> {
					var elem = readElemIdx();
					yield new TableInstr.Elem_Drop(elem);
				}

				case 14 -> {
					var dest = readTableIdx();
					var src = readTableIdx();
					yield new TableInstr.Table_Copy(dest, src);
				}

				case 15 -> {
					var table = readTableIdx();
					yield new TableInstr.Table_Grow(table);
				}

				case 16 -> {
					var table = readTableIdx();
					yield new TableInstr.Table_Size(table);
				}

				case 17 -> {
					var table = readTableIdx();
					yield new TableInstr.Table_Fill(table);
				}

				default -> throw new ModuleFormatException("illegal opcode");
			};

			case 0xFD -> {
				int vecOp = readU32();
				yield switch(vecOp) {
					case 0 -> {
						var memArg = readMemArg();
						yield new MemoryInstr.V128_Load(memArg);
					}
					case 1 -> {
						var memArg = readMemArg();
						yield new MemoryInstr.V128_Load8x8_S(memArg);
					}
					case 2 -> {
						var memArg = readMemArg();
						yield new MemoryInstr.V128_Load8x8_U(memArg);
					}
					case 3 -> {
						var memArg = readMemArg();
						yield new MemoryInstr.V128_Load16x4_S(memArg);
					}
					case 4 -> {
						var memArg = readMemArg();
						yield new MemoryInstr.V128_Load16x4_U(memArg);
					}
					case 5 -> {
						var memArg = readMemArg();
						yield new MemoryInstr.V128_Load32x2_S(memArg);
					}
					case 6 -> {
						var memArg = readMemArg();
						yield new MemoryInstr.V128_Load32x2_U(memArg);
					}
					case 7 -> {
						var memArg = readMemArg();
						yield new MemoryInstr.V128_Load8_Splat(memArg);
					}
					case 8 -> {
						var memArg = readMemArg();
						yield new MemoryInstr.V128_Load16_Splat(memArg);
					}
					case 9 -> {
						var memArg = readMemArg();
						yield new MemoryInstr.V128_Load32_Splat(memArg);
					}
					case 10 -> {
						var memArg = readMemArg();
						yield new MemoryInstr.V128_Load64_Splat(memArg);
					}
					case 92 -> {
						var memArg = readMemArg();
						yield new MemoryInstr.V128_Load32_Zero(memArg);
					}
					case 93 -> {
						var memArg = readMemArg();
						yield new MemoryInstr.V128_Load64_Zero(memArg);
					}
					case 11 -> {
						var memArg = readMemArg();
						yield new MemoryInstr.V128_Store(memArg);
					}
					case 84 -> {
						var memArg = readMemArg();
						byte laneIdx = readByte();
						yield new MemoryInstr.V128_Load8_Lane(memArg, laneIdx);
					}
					case 85 -> {
						var memArg = readMemArg();
						byte laneIdx = readByte();
						yield new MemoryInstr.V128_Load16_Lane(memArg, laneIdx);
					}
					case 86 -> {
						var memArg = readMemArg();
						byte laneIdx = readByte();
						yield new MemoryInstr.V128_Load32_Lane(memArg, laneIdx);
					}
					case 87 -> {
						var memArg = readMemArg();
						byte laneIdx = readByte();
						yield new MemoryInstr.V128_Load64_Lane(memArg, laneIdx);
					}
					case 88 -> {
						var memArg = readMemArg();
						byte laneIdx = readByte();
						yield new MemoryInstr.V128_Store8_Lane(memArg, laneIdx);
					}
					case 89 -> {
						var memArg = readMemArg();
						byte laneIdx = readByte();
						yield new MemoryInstr.V128_Store16_Lane(memArg, laneIdx);
					}
					case 90 -> {
						var memArg = readMemArg();
						byte laneIdx = readByte();
						yield new MemoryInstr.V128_Store32_Lane(memArg, laneIdx);
					}
					case 91 -> {
						var memArg = readMemArg();
						byte laneIdx = readByte();
						yield new MemoryInstr.V128_Store64_Lane(memArg, laneIdx);
					}
					
					case 12 -> {
						byte b0 = readByte();
						byte b1 = readByte();
						byte b2 = readByte();
						byte b3 = readByte();
						byte b4 = readByte();
						byte b5 = readByte();
						byte b6 = readByte();
						byte b7 = readByte();
						byte b8 = readByte();
						byte b9 = readByte();
						byte b10 = readByte();
						byte b11 = readByte();
						byte b12 = readByte();
						byte b13 = readByte();
						byte b14 = readByte();
						byte b15 = readByte();
						
						yield new VectorInstr.V128_Const(new V128(
								b0,
								b1,
								b2,
								b3,
								b4,
								b5,
								b6,
								b7,
								b8,
								b9,
								b10,
								b11,
								b12,
								b13,
								b14,
								b15
						));
					}
					
					case 13 -> {
						byte laneIdx0 = readByte();
						byte laneIdx1 = readByte();
						byte laneIdx2 = readByte();
						byte laneIdx3 = readByte();
						byte laneIdx4 = readByte();
						byte laneIdx5 = readByte();
						byte laneIdx6 = readByte();
						byte laneIdx7 = readByte();
						byte laneIdx8 = readByte();
						byte laneIdx9 = readByte();
						byte laneIdx10 = readByte();
						byte laneIdx11 = readByte();
						byte laneIdx12 = readByte();
						byte laneIdx13 = readByte();
						byte laneIdx14 = readByte();
						byte laneIdx15 = readByte();

						yield new VectorInstr.I8x16_Op_Instr(new VectorInstr.Shuffle(new V128(
								laneIdx0,
								laneIdx1,
								laneIdx2,
								laneIdx3,
								laneIdx4,
								laneIdx5,
								laneIdx6,
								laneIdx7,
								laneIdx8,
								laneIdx9,
								laneIdx10,
								laneIdx11,
								laneIdx12,
								laneIdx13,
								laneIdx14,
								laneIdx15
						)));
					}

					case 21 -> {
						byte laneIndex = readByte();
						yield new VectorInstr.I8x16_Op_Instr(new VectorInstr.ExtractLane_S(laneIndex));
					}
					case 22 -> {
						byte laneIndex = readByte();
						yield new VectorInstr.I8x16_Op_Instr(new VectorInstr.ExtractLane_U(laneIndex));
					}
					case 23 -> {
						byte laneIndex = readByte();
						yield new VectorInstr.I8x16_Op_Instr(new VectorInstr.ReplaceLane(laneIndex));
					}

					case 24 -> {
						byte laneIndex = readByte();
						yield new VectorInstr.I16x8_Op_Instr(new VectorInstr.ExtractLane_S(laneIndex));
					}
					case 25 -> {
						byte laneIndex = readByte();
						yield new VectorInstr.I16x8_Op_Instr(new VectorInstr.ExtractLane_U(laneIndex));
					}
					case 26 -> {
						byte laneIndex = readByte();
						yield new VectorInstr.I16x8_Op_Instr(new VectorInstr.ReplaceLane(laneIndex));
					}

					case 27 -> {
						byte laneIndex = readByte();
						yield new VectorInstr.I32x4_Op_Instr(new VectorInstr.ExtractLane(laneIndex));
					}
					case 28 -> {
						byte laneIndex = readByte();
						yield new VectorInstr.I32x4_Op_Instr(new VectorInstr.ReplaceLane(laneIndex));
					}

					case 29 -> {
						byte laneIndex = readByte();
						yield new VectorInstr.I64x2_Op_Instr(new VectorInstr.ExtractLane(laneIndex));
					}
					case 30 -> {
						byte laneIndex = readByte();
						yield new VectorInstr.I64x2_Op_Instr(new VectorInstr.ReplaceLane(laneIndex));
					}

					case 31 -> {
						byte laneIndex = readByte();
						yield new VectorInstr.F32x4_Op_Instr(new VectorInstr.ExtractLane(laneIndex));
					}
					case 32 -> {
						byte laneIndex = readByte();
						yield new VectorInstr.F32x4_Op_Instr(new VectorInstr.ReplaceLane(laneIndex));
					}

					case 33 -> {
						byte laneIndex = readByte();
						yield new VectorInstr.F64x2_Op_Instr(new VectorInstr.ExtractLane(laneIndex));
					}
					case 34 -> {
						byte laneIndex = readByte();
						yield new VectorInstr.F64x2_Op_Instr(new VectorInstr.ReplaceLane(laneIndex));
					}

					case 14 -> new VectorInstr.I8x16_Op_Instr(new VectorInstr.Swizzle());
					case 15 -> new VectorInstr.I8x16_Op_Instr(new VectorInstr.Splat());
					case 16 -> new VectorInstr.I16x8_Op_Instr(new VectorInstr.Splat());
					case 17 -> new VectorInstr.I32x4_Op_Instr(new VectorInstr.Splat());
					case 18 -> new VectorInstr.I64x2_Op_Instr(new VectorInstr.Splat());
					case 19 -> new VectorInstr.F32x4_Op_Instr(new VectorInstr.Splat());
					case 20 -> new VectorInstr.F64x2_Op_Instr(new VectorInstr.Splat());


					case 35 -> new VectorInstr.I8x16_Op_Instr(VectorInstr.VIRelOp_S.EQ);
					case 36 -> new VectorInstr.I8x16_Op_Instr(VectorInstr.VIRelOp_S.NE);
					case 37 -> new VectorInstr.I8x16_Op_Instr(VectorInstr.VIRelOp_S.LT_S);
					case 38 -> new VectorInstr.I8x16_Op_Instr(VectorInstr.VIRelOp_U.LT_U);
					case 39 -> new VectorInstr.I8x16_Op_Instr(VectorInstr.VIRelOp_S.GT_S);
					case 40 -> new VectorInstr.I8x16_Op_Instr(VectorInstr.VIRelOp_U.GT_U);
					case 41 -> new VectorInstr.I8x16_Op_Instr(VectorInstr.VIRelOp_S.LE_S);
					case 42 -> new VectorInstr.I8x16_Op_Instr(VectorInstr.VIRelOp_U.LE_U);
					case 43 -> new VectorInstr.I8x16_Op_Instr(VectorInstr.VIRelOp_S.GE_S);
					case 44 -> new VectorInstr.I8x16_Op_Instr(VectorInstr.VIRelOp_U.GE_U);

					case 45 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VIRelOp_S.EQ);
					case 46 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VIRelOp_S.NE);
					case 47 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VIRelOp_S.LT_S);
					case 48 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VIRelOp_U.LT_U);
					case 49 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VIRelOp_S.GT_S);
					case 50 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VIRelOp_U.GT_U);
					case 51 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VIRelOp_S.LE_S);
					case 52 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VIRelOp_U.LE_U);
					case 53 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VIRelOp_S.GE_S);
					case 54 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VIRelOp_U.GE_U);

					case 55 -> new VectorInstr.I32x4_Op_Instr(VectorInstr.VIRelOp_S.EQ);
					case 56 -> new VectorInstr.I32x4_Op_Instr(VectorInstr.VIRelOp_S.NE);
					case 57 -> new VectorInstr.I32x4_Op_Instr(VectorInstr.VIRelOp_S.LT_S);
					case 58 -> new VectorInstr.I32x4_Op_Instr(VectorInstr.VIRelOp_U.LT_U);
					case 59 -> new VectorInstr.I32x4_Op_Instr(VectorInstr.VIRelOp_S.GT_S);
					case 60 -> new VectorInstr.I32x4_Op_Instr(VectorInstr.VIRelOp_U.GT_U);
					case 61 -> new VectorInstr.I32x4_Op_Instr(VectorInstr.VIRelOp_S.LE_S);
					case 62 -> new VectorInstr.I32x4_Op_Instr(VectorInstr.VIRelOp_U.LE_U);
					case 63 -> new VectorInstr.I32x4_Op_Instr(VectorInstr.VIRelOp_S.GE_S);
					case 64 -> new VectorInstr.I32x4_Op_Instr(VectorInstr.VIRelOp_U.GE_U);

					case 214 -> new VectorInstr.I64x2_Op_Instr(VectorInstr.VIRelOp_S.EQ);
					case 215 -> new VectorInstr.I64x2_Op_Instr(VectorInstr.VIRelOp_S.NE);
					case 216 -> new VectorInstr.I64x2_Op_Instr(VectorInstr.VIRelOp_S.LT_S);
					case 217 -> new VectorInstr.I64x2_Op_Instr(VectorInstr.VIRelOp_S.GT_S);
					case 218 -> new VectorInstr.I64x2_Op_Instr(VectorInstr.VIRelOp_S.LE_S);
					case 219 -> new VectorInstr.I64x2_Op_Instr(VectorInstr.VIRelOp_S.GE_S);

					case 65 -> new VectorInstr.F32x4_Op_Instr(VectorInstr.VFRelOp.EQ);
					case 66 -> new VectorInstr.F32x4_Op_Instr(VectorInstr.VFRelOp.NE);
					case 67 -> new VectorInstr.F32x4_Op_Instr(VectorInstr.VFRelOp.LT);
					case 68 -> new VectorInstr.F32x4_Op_Instr(VectorInstr.VFRelOp.GT);
					case 69 -> new VectorInstr.F32x4_Op_Instr(VectorInstr.VFRelOp.LE);
					case 70 -> new VectorInstr.F32x4_Op_Instr(VectorInstr.VFRelOp.GE);

					case 71 -> new VectorInstr.F64x2_Op_Instr(VectorInstr.VFRelOp.EQ);
					case 72 -> new VectorInstr.F64x2_Op_Instr(VectorInstr.VFRelOp.NE);
					case 73 -> new VectorInstr.F64x2_Op_Instr(VectorInstr.VFRelOp.LT);
					case 74 -> new VectorInstr.F64x2_Op_Instr(VectorInstr.VFRelOp.GT);
					case 75 -> new VectorInstr.F64x2_Op_Instr(VectorInstr.VFRelOp.LE);
					case 76 -> new VectorInstr.F64x2_Op_Instr(VectorInstr.VFRelOp.GE);

					case 77 -> VectorInstr.VVUnOp.NOT;
					case 78 -> VectorInstr.VVBinOp.AND;
					case 79 -> VectorInstr.VVBinOp.ANDNOT;
					case 80 -> VectorInstr.VVBinOp.OR;
					case 81 -> VectorInstr.VVBinOp.XOR;
					case 82 -> VectorInstr.VVTernOp.BITSELECT;
					case 83 -> VectorInstr.VVTestOp.ANY_TRUE;

					case 96 -> new VectorInstr.I8x16_Op_Instr(VectorInstr.VIUnOp.ABS);
					case 97 -> new VectorInstr.I8x16_Op_Instr(VectorInstr.VIUnOp.NEG);
					case 98 -> new VectorInstr.I8x16_Op_Instr(new VectorInstr.Popcnt());
					case 99 -> new VectorInstr.I8x16_Op_Instr(new VectorInstr.All_True());
					case 100 -> new VectorInstr.I8x16_Op_Instr(new VectorInstr.BitMask());
					case 101 -> new VectorInstr.I8x16_Op_Instr(new VectorInstr.I8x16_Narrow_I16x8_S());
					case 102 -> new VectorInstr.I8x16_Op_Instr(new VectorInstr.I8x16_Narrow_I16x8_U());
					case 107 -> new VectorInstr.I8x16_Op_Instr(VectorInstr.VIShiftOp.SHL);
					case 108 -> new VectorInstr.I8x16_Op_Instr(VectorInstr.VIShiftOp.SHR_S);
					case 109 -> new VectorInstr.I8x16_Op_Instr(VectorInstr.VIShiftOp.SHR_U);
					case 110 -> new VectorInstr.I8x16_Op_Instr(VectorInstr.VIBinOp.ADD);
					case 111 -> new VectorInstr.I8x16_Op_Instr(VectorInstr.VISatBinOp.ADD_SAT_S);
					case 112 -> new VectorInstr.I8x16_Op_Instr(VectorInstr.VISatBinOp.ADD_SAT_U);
					case 113 -> new VectorInstr.I8x16_Op_Instr(VectorInstr.VIBinOp.SUB);
					case 114 -> new VectorInstr.I8x16_Op_Instr(VectorInstr.VISatBinOp.SUB_SAT_S);
					case 115 -> new VectorInstr.I8x16_Op_Instr(VectorInstr.VISatBinOp.SUB_SAT_U);
					case 118 -> new VectorInstr.I8x16_Op_Instr(VectorInstr.VIMinMaxOp.MIN_S);
					case 119 -> new VectorInstr.I8x16_Op_Instr(VectorInstr.VIMinMaxOp.MIN_U);
					case 120 -> new VectorInstr.I8x16_Op_Instr(VectorInstr.VIMinMaxOp.MAX_S);
					case 121 -> new VectorInstr.I8x16_Op_Instr(VectorInstr.VIMinMaxOp.MAX_U);
					case 123 -> new VectorInstr.I8x16_Op_Instr(VectorInstr.VIAverageOps.AVGR_U);

					case 124 -> new VectorInstr.I16x8_Op_Instr(new VectorInstr.I16x8_ExtAdd_Pairwise_I8x16_S());
					case 125 -> new VectorInstr.I16x8_Op_Instr(new VectorInstr.I16x8_ExtAdd_Pairwise_I8x16_U());
					case 128 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VIUnOp.ABS);
					case 129 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VIUnOp.NEG);
					case 130 -> new VectorInstr.I16x8_Op_Instr(new VectorInstr.Q15mulr_Sat_S());
					case 131 -> new VectorInstr.I16x8_Op_Instr(new VectorInstr.All_True());
					case 132 -> new VectorInstr.I16x8_Op_Instr(new VectorInstr.BitMask());
					case 133 -> new VectorInstr.I16x8_Op_Instr(new VectorInstr.I16x8_Narrow_I32x4_S());
					case 134 -> new VectorInstr.I16x8_Op_Instr(new VectorInstr.I16x8_Narrow_I32x4_U());
					case 135 -> new VectorInstr.I16x8_Op_Instr(new VectorInstr.I16x8_Extend_Low_I8x16_S());
					case 136 -> new VectorInstr.I16x8_Op_Instr(new VectorInstr.I16x8_Extend_High_I8x16_S());
					case 137 -> new VectorInstr.I16x8_Op_Instr(new VectorInstr.I16x8_Extend_Low_I8x16_U());
					case 138 -> new VectorInstr.I16x8_Op_Instr(new VectorInstr.I16x8_Extend_High_I8x16_U());
					case 139 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VIShiftOp.SHL);
					case 140 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VIShiftOp.SHR_S);
					case 141 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VIShiftOp.SHR_U);
					case 142 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VIBinOp.ADD);
					case 143 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VISatBinOp.ADD_SAT_S);
					case 144 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VISatBinOp.ADD_SAT_U);
					case 145 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VIBinOp.SUB);
					case 146 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VISatBinOp.SUB_SAT_S);
					case 147 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VISatBinOp.SUB_SAT_U);
					case 149 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VIMulOp.MUL);
					case 150 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VIMinMaxOp.MIN_S);
					case 151 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VIMinMaxOp.MIN_U);
					case 152 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VIMinMaxOp.MAX_S);
					case 153 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VIMinMaxOp.MAX_U);
					case 155 -> new VectorInstr.I16x8_Op_Instr(VectorInstr.VIAverageOps.AVGR_U);
					case 156 -> new VectorInstr.I16x8_Op_Instr(new VectorInstr.I16x8_ExtMul_Low_I8x16_S());
					case 157 -> new VectorInstr.I16x8_Op_Instr(new VectorInstr.I16x8_ExtMul_High_I8x16_S());
					case 158 -> new VectorInstr.I16x8_Op_Instr(new VectorInstr.I16x8_ExtMul_Low_I8x16_U());
					case 159 -> new VectorInstr.I16x8_Op_Instr(new VectorInstr.I16x8_ExtMul_High_I8x16_U());

					case 126 -> new VectorInstr.I32x4_Op_Instr(new VectorInstr.I32x4_ExtAdd_Pairwise_I16x8_S());
					case 127 -> new VectorInstr.I32x4_Op_Instr(new VectorInstr.I32x4_ExtAdd_Pairwise_I16x8_U());
					case 160 -> new VectorInstr.I32x4_Op_Instr(VectorInstr.VIUnOp.ABS);
					case 161 -> new VectorInstr.I32x4_Op_Instr(VectorInstr.VIUnOp.NEG);
					case 163 -> new VectorInstr.I32x4_Op_Instr(new VectorInstr.All_True());
					case 164 -> new VectorInstr.I32x4_Op_Instr(new VectorInstr.BitMask());
					case 167 -> new VectorInstr.I32x4_Op_Instr(new VectorInstr.I32x4_Extend_Low_I16x8_S());
					case 168 -> new VectorInstr.I32x4_Op_Instr(new VectorInstr.I32x4_Extend_High_I16x8_S());
					case 169 -> new VectorInstr.I32x4_Op_Instr(new VectorInstr.I32x4_Extend_Low_I16x8_U());
					case 170 -> new VectorInstr.I32x4_Op_Instr(new VectorInstr.I32x4_Extend_High_I16x8_U());
					case 171 -> new VectorInstr.I32x4_Op_Instr(VectorInstr.VIShiftOp.SHL);
					case 172 -> new VectorInstr.I32x4_Op_Instr(VectorInstr.VIShiftOp.SHR_S);
					case 173 -> new VectorInstr.I32x4_Op_Instr(VectorInstr.VIShiftOp.SHR_U);
					case 174 -> new VectorInstr.I32x4_Op_Instr(VectorInstr.VIBinOp.ADD);
					case 177 -> new VectorInstr.I32x4_Op_Instr(VectorInstr.VIBinOp.SUB);
					case 181 -> new VectorInstr.I32x4_Op_Instr(VectorInstr.VIMulOp.MUL);
					case 182 -> new VectorInstr.I32x4_Op_Instr(VectorInstr.VIMinMaxOp.MIN_S);
					case 183 -> new VectorInstr.I32x4_Op_Instr(VectorInstr.VIMinMaxOp.MIN_U);
					case 184 -> new VectorInstr.I32x4_Op_Instr(VectorInstr.VIMinMaxOp.MAX_S);
					case 185 -> new VectorInstr.I32x4_Op_Instr(VectorInstr.VIMinMaxOp.MAX_U);
					case 186 -> new VectorInstr.I32x4_Op_Instr(new VectorInstr.Dot_I16x8_S());
					case 188 -> new VectorInstr.I32x4_Op_Instr(new VectorInstr.I32x4_ExtMul_Low_I16x8_S());
					case 189 -> new VectorInstr.I32x4_Op_Instr(new VectorInstr.I32x4_ExtMul_High_I16x8_S());
					case 190 -> new VectorInstr.I32x4_Op_Instr(new VectorInstr.I32x4_ExtMul_Low_I16x8_U());
					case 191 -> new VectorInstr.I32x4_Op_Instr(new VectorInstr.I32x4_ExtMul_High_I16x8_U());

					case 192 -> new VectorInstr.I64x2_Op_Instr(VectorInstr.VIUnOp.ABS);
					case 193 -> new VectorInstr.I64x2_Op_Instr(VectorInstr.VIUnOp.NEG);
					case 195 -> new VectorInstr.I64x2_Op_Instr(new VectorInstr.All_True());
					case 196 -> new VectorInstr.I64x2_Op_Instr(new VectorInstr.BitMask());
					case 199 -> new VectorInstr.I64x2_Op_Instr(new VectorInstr.I64x2_Extend_Low_I32x4_S());
					case 200 -> new VectorInstr.I64x2_Op_Instr(new VectorInstr.I64x2_Extend_High_I32x4_S());
					case 201 -> new VectorInstr.I64x2_Op_Instr(new VectorInstr.I64x2_Extend_Low_I32x4_U());
					case 202 -> new VectorInstr.I64x2_Op_Instr(new VectorInstr.I64x2_Extend_High_I32x4_U());
					case 203 -> new VectorInstr.I64x2_Op_Instr(VectorInstr.VIShiftOp.SHL);
					case 204 -> new VectorInstr.I64x2_Op_Instr(VectorInstr.VIShiftOp.SHR_S);
					case 205 -> new VectorInstr.I64x2_Op_Instr(VectorInstr.VIShiftOp.SHR_U);
					case 206 -> new VectorInstr.I64x2_Op_Instr(VectorInstr.VIBinOp.ADD);
					case 209 -> new VectorInstr.I64x2_Op_Instr(VectorInstr.VIBinOp.SUB);
					case 213 -> new VectorInstr.I64x2_Op_Instr(VectorInstr.VIMulOp.MUL);
					case 220 -> new VectorInstr.I64x2_Op_Instr(new VectorInstr.I64x2_ExtMul_Low_I32x4_S());
					case 221 -> new VectorInstr.I64x2_Op_Instr(new VectorInstr.I64x2_ExtMul_High_I32x4_S());
					case 222 -> new VectorInstr.I64x2_Op_Instr(new VectorInstr.I64x2_ExtMul_Low_I32x4_U());
					case 223 -> new VectorInstr.I64x2_Op_Instr(new VectorInstr.I64x2_ExtMul_High_I32x4_U());
					
					case 103 -> new VectorInstr.F32x4_Op_Instr(VectorInstr.VFUnOp.CEIL);
					case 104 -> new VectorInstr.F32x4_Op_Instr(VectorInstr.VFUnOp.FLOOR);
					case 105 -> new VectorInstr.F32x4_Op_Instr(VectorInstr.VFUnOp.TRUNC);
					case 106 -> new VectorInstr.F32x4_Op_Instr(VectorInstr.VFUnOp.NEAREST);
					case 224 -> new VectorInstr.F32x4_Op_Instr(VectorInstr.VFUnOp.ABS);
					case 225 -> new VectorInstr.F32x4_Op_Instr(VectorInstr.VFUnOp.NEG);
					case 227 -> new VectorInstr.F32x4_Op_Instr(VectorInstr.VFUnOp.SQRT);
					case 228 -> new VectorInstr.F32x4_Op_Instr(VectorInstr.VFBinOp.ADD);
					case 229 -> new VectorInstr.F32x4_Op_Instr(VectorInstr.VFBinOp.SUB);
					case 230 -> new VectorInstr.F32x4_Op_Instr(VectorInstr.VFBinOp.MUL);
					case 231 -> new VectorInstr.F32x4_Op_Instr(VectorInstr.VFBinOp.DIV);
					case 232 -> new VectorInstr.F32x4_Op_Instr(VectorInstr.VFBinOp.MIN);
					case 233 -> new VectorInstr.F32x4_Op_Instr(VectorInstr.VFBinOp.MAX);
					case 234 -> new VectorInstr.F32x4_Op_Instr(VectorInstr.VFBinOp.PMIN);
					case 235 -> new VectorInstr.F32x4_Op_Instr(VectorInstr.VFBinOp.PMAX);

					case 116 -> new VectorInstr.F64x2_Op_Instr(VectorInstr.VFUnOp.CEIL);
					case 117 -> new VectorInstr.F64x2_Op_Instr(VectorInstr.VFUnOp.FLOOR);
					case 122 -> new VectorInstr.F64x2_Op_Instr(VectorInstr.VFUnOp.TRUNC);
					case 148 -> new VectorInstr.F64x2_Op_Instr(VectorInstr.VFUnOp.NEAREST);
					case 236 -> new VectorInstr.F64x2_Op_Instr(VectorInstr.VFUnOp.ABS);
					case 237 -> new VectorInstr.F64x2_Op_Instr(VectorInstr.VFUnOp.NEG);
					case 239 -> new VectorInstr.F64x2_Op_Instr(VectorInstr.VFUnOp.SQRT);
					case 240 -> new VectorInstr.F64x2_Op_Instr(VectorInstr.VFBinOp.ADD);
					case 241 -> new VectorInstr.F64x2_Op_Instr(VectorInstr.VFBinOp.SUB);
					case 242 -> new VectorInstr.F64x2_Op_Instr(VectorInstr.VFBinOp.MUL);
					case 243 -> new VectorInstr.F64x2_Op_Instr(VectorInstr.VFBinOp.DIV);
					case 244 -> new VectorInstr.F64x2_Op_Instr(VectorInstr.VFBinOp.MIN);
					case 245 -> new VectorInstr.F64x2_Op_Instr(VectorInstr.VFBinOp.MAX);
					case 246 -> new VectorInstr.F64x2_Op_Instr(VectorInstr.VFBinOp.PMIN);
					case 247 -> new VectorInstr.F64x2_Op_Instr(VectorInstr.VFBinOp.PMAX);

					case 248 -> new VectorInstr.I32x4_Op_Instr(new VectorInstr.I32x4_Trunc_Sat_F32x4_S());
					case 249 -> new VectorInstr.I32x4_Op_Instr(new VectorInstr.I32x4_Trunc_Sat_F32x4_U());
					case 250 -> new VectorInstr.F32x4_Op_Instr(new VectorInstr.F32x4_Convert_I32x4_S());
					case 251 -> new VectorInstr.F32x4_Op_Instr(new VectorInstr.F32x4_Convert_I32x4_U());
					case 252 -> new VectorInstr.I32x4_Op_Instr(new VectorInstr.I32x4_Trunc_Sat_F64x4_S_Zero());
					case 253 -> new VectorInstr.I32x4_Op_Instr(new VectorInstr.I32x4_Trunc_Sat_F64x4_U_Zero());
					case 254 -> new VectorInstr.F64x2_Op_Instr(new VectorInstr.F64x2_Convert_Low_I32x4_S());
					case 255 -> new VectorInstr.F64x2_Op_Instr(new VectorInstr.F64x2_Convert_Low_I32x4_U());
					case 94 -> new VectorInstr.F32x4_Op_Instr(new VectorInstr.F32x4_Demote_F64x2_Zero());
					case 95 -> new VectorInstr.F64x2_Op_Instr(new VectorInstr.F64x2_Promote_Low_F32x4());

					default -> throw new ModuleFormatException("Unsupported Vector opcode: " + Integer.toUnsignedString(vecOp));
				};
			}


			default -> throw new ModuleFormatException("illegal opcode");
		};
	}

	public MemoryInstr.MemArg readMemArg() throws IOException, ModuleFormatException {
		var align = readU32();
		var offset = readU32();

		if(align >= 32) {
			throw new ModuleFormatException("malformed memop flags");
		}

		return new MemoryInstr.MemArg(offset, align);
	}


	public Expr readExpr() throws IOException, ModuleFormatException {
		List<Instr> instrs = new ArrayList<>();
		while(true) {
			var instr = readInstrOrTerminator();
			if(instr == BlockTerminator.END) {
				break;
			}
			else if(instr instanceof Instr i) {
				instrs.add(i);
			}
			else {
				throw new ModuleFormatException("END opcode expected");
			}
		}

		return new Expr(instrs);
	}


	public LabelIdx readLabelIdx() throws IOException, ModuleFormatException {
		return new LabelIdx(readU32());
	}

	public FuncIdx readFuncIdx() throws IOException, ModuleFormatException {
		return new FuncIdx(readU32());
	}
	public TypeIdx readTypeIdx() throws IOException, ModuleFormatException {
		return new TypeIdx(readU32());
	}
	public TableIdx readTableIdx() throws IOException, ModuleFormatException {
		return new TableIdx(readU32());
	}
	public LocalIdx readLocalIdx() throws IOException, ModuleFormatException {
		return new LocalIdx(readU32());
	}
	public GlobalIdx readGlobalIdx() throws IOException, ModuleFormatException {
		return new GlobalIdx(readU32());
	}
	public ElemIdx readElemIdx() throws IOException, ModuleFormatException {
		return new ElemIdx(readU32());
	}
	public DataIdx readDataIdx() throws IOException, ModuleFormatException {
		if(!hasDataCount) {
			throw new ModuleFormatException("data count section required");
		}

		return new DataIdx(readU32());
	}
	public MemIdx readMemIdx() throws IOException, ModuleFormatException {
		return new MemIdx(readU32());
	}

	public List<? extends FuncType> readTypeSectionContent() throws IOException, ModuleFormatException {
		return readVector(this::readFuncType);
	}

	public List<? extends Import> readImportSectionContent() throws IOException, ModuleFormatException {
		return readVector(() -> {
			var mod = readName();
			var importName = readName();

			var desc = switch(readByte()) {
				case 0x00 -> {
					var type = readTypeIdx();
					yield new ImportDesc.Func(type);
				}

				case 0x01 -> {
					var type = readTableType();
					yield new ImportDesc.Table(type);
				}

				case 0x02 -> {
					var type = readMemType();
					yield new ImportDesc.Mem(type);
				}

				case 0x03 -> {
					var type = readGlobalType();
					yield new ImportDesc.Global(type);
				}

				default -> throw new ModuleFormatException("malformed import kind");
			};

			return new Import(mod, importName, desc);
		});
	}

	public List<? extends TypeIdx> readFunctionSectionContent() throws IOException, ModuleFormatException {
		return readVector(this::readTypeIdx);
	}

	public List<? extends Table> readTableSectionContent() throws IOException, ModuleFormatException {
		return readVector(() -> {
			var type = readTableType();
			return new Table(type);
		});
	}

	public List<? extends Mem> readMemorySectionContent() throws IOException, ModuleFormatException {
		return readVector(() -> {
			var type = readMemType();
			return new Mem(type);
		});
	}

	public List<? extends Global> readGlobalSectionContent() throws IOException, ModuleFormatException {
		return readVector(() -> {
			var type = readGlobalType();
			var expr = readExpr();
			return new Global(type, expr);
		});
	}

	public List<? extends Export> readExportSectionContent() throws IOException, ModuleFormatException {
		return readVector(() -> {
			var name = readName();

			var desc = switch(readByte()) {
				case 0x00 -> {
					var func = readFuncIdx();
					yield new ExportDesc.Func(func);
				}

				case 0x01 -> {
					var table = readTableIdx();
					yield new ExportDesc.Table(table);
				}

				case 0x02 -> {
					var mem = readMemIdx();
					yield new ExportDesc.Mem(mem);
				}

				case 0x03 -> {
					var global = readGlobalIdx();
					yield new ExportDesc.Global(global);
				}

				default -> throw new ModuleFormatException("illegal export descriptor");
			};

			return new Export(name, desc);
		});
	}

	public Start readStartSectionContent() throws IOException, ModuleFormatException {
		var func = readFuncIdx();
		return new Start(func);
	}

	public List<? extends Elem> readElementSectionContent() throws IOException, ModuleFormatException {
		return readVector(() -> {
			var elemSpec = readU32();

			ElemMode mode;
			if((elemSpec & 0x01) == 0x01) {
				if((elemSpec & 0x02) == 0x02) {
					mode = new ElemMode.Declarative();
				}
				else {
					mode = new ElemMode.Passive();
				}
			}
			else {
				TableIdx table;
				if((elemSpec & 0x02) == 0x02) {
					table = readTableIdx();
				}
				else {
					table = new TableIdx(0);
				}

				var offset = readExpr();

				mode = new ElemMode.Active(table, offset);
			}

			RefType type;
			List<? extends Expr> init;
			if((elemSpec & 0x04) == 0x04) {

				if((elemSpec & 0x03) != 0x00) {
					type = readRefType();
				}
				else {
					type = new FuncRef();
				}

				init = readVector(this::readExpr);
			}
			else {
				ElemKind elemKind;
				if((elemSpec & 0x03) != 0x00) {
					elemKind = readElemKind();
				}
				else {
					elemKind = ElemKind.FUNC_REF;
				}

				type = elemKind.getRefType();

				init = switch(elemKind) {
					case FUNC_REF -> readVector(() -> {
						var funcIdx = readFuncIdx();
						return new Expr(List.of(new ReferenceInstr.Ref_Func(funcIdx)));
					});
				};
			}

			return new Elem(type, init, mode);
		});
	}

	public enum ElemKind {
		FUNC_REF(new FuncRef()),
		;

		ElemKind(RefType refType) {
			this.refType = refType;
		}

		private RefType refType;

		public RefType getRefType() {
			return refType;
		}
	}
	public ElemKind readElemKind() throws IOException, ModuleFormatException {
		var b = readByte();
		if(b != 0) {
			throw new ModuleFormatException("Invalid element type");
		}

		return ElemKind.FUNC_REF;
	}

	private record LocalDeclaration(int n, ValType t) {}

	public List<? extends Code> readCodeSectionContent() throws IOException, ModuleFormatException {
		return readVector(() -> {
			readU32(); // Size

			var localDecls = readVector(() -> {
				int n = readU32();
				var t = readValType();
				return new LocalDeclaration(n, t);
			});

			long totalDeclarations = 0;
			for(var decl : localDecls) {
				totalDeclarations += Integer.toUnsignedLong(decl.n());
				if(totalDeclarations >= (1L << 32)) {
					throw new ModuleFormatException("too many locals");
				}
			}

			var locals = localDecls.stream().flatMap(decl -> Stream.generate(() -> decl.t()).limit(decl.n())).toList();

			var body = readExpr();

			return new Code(locals, body);
		});
	}

	public static record Code(List<? extends ValType> locals, Expr body) {}

	public List<? extends Data> readDataSectionContent() throws IOException, ModuleFormatException {
		return readVector(() -> switch(readU32()) {
			case 0 -> {
				var offset = readExpr();
				var data = readByteVec();
				yield new Data(data, new DataMode.Active(new MemIdx(0), offset));
			}

			case 1 -> {
				var data = readByteVec();
				yield new Data(data, new DataMode.Passive());
			}

			case 2 -> {
				var mem = readMemIdx();
				var offset = readExpr();
				var data = readByteVec();
				yield new Data(data, new DataMode.Active(mem, offset));
			}
			
			default -> throw new ModuleFormatException("illegal data mode");
		});
	}

	public int readDataCountSectionContent() throws IOException, ModuleFormatException {
		return readU32();
	}

	private static final int[] SECTION_ORDER = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 12, 10, 11 };

	private int getSectionIndex(int section) {
		for(int i = 0; i < SECTION_ORDER.length; ++i) {
			if(SECTION_ORDER[i] == section) {
				return i;
			}
		}

		return -1;
	}


	public Module readModule() throws IOException, ModuleFormatException {
		List<? extends FuncType> types = new ArrayList<>();
		List<? extends TypeIdx> funcTypes = new ArrayList<>();
		List<? extends Table> tables = new ArrayList<>();
		List<? extends Mem> mems = new ArrayList<>();
		List<? extends Global> globals = new ArrayList<>();
		List<? extends Elem> elems = new ArrayList<>();
		List<? extends Data> datas = new ArrayList<>();
		@Nullable Start start = null;
		List<? extends Import> imports = new ArrayList<>();
		List<? extends Export> exports = new ArrayList<>();
		List<? extends Code> codeSec = new ArrayList<>();
		int dataCount = 0;

		try {
			int magic = readFixedInt();
			if(magic != 0x6D736100) {
				throw new ModuleFormatException("magic header not detected");
			}

			int version = readFixedInt();
			if(version != 1) {
				throw new ModuleFormatException("unknown binary version");
			}

			int lastSection = 0;

			while(true) {
				int section = is.read();
				if(section < 0) {
					break;
				}
				++offset;

				int size = readU32();

				System.err.println("section " + section);

				int sectionIndex = getSectionIndex(section);
				// If the section id is invalid, allow it to reach the failing case.
				if(section != 0 && sectionIndex >= 0 && sectionIndex <= getSectionIndex(lastSection)) {
					throw new ModuleFormatException("unexpected content after last section");
				}

				switch(section) {
					case 0 -> {
						readSection(size, () -> {
							long beforeName = offset;
							String name = readName();
							long remaining = size - (offset - beforeName);
							while(remaining > 0) {
								long skipped = is.skip(remaining);
								remaining -= skipped;
								offset += skipped;
								if(skipped < 1) {
									readByte();
									--remaining;
								}
							}
							return name;
						});
					}
					case 1 -> {
						types = readSection(size, this::readTypeSectionContent);
					}
					case 2 -> {
						imports = readSection(size, this::readImportSectionContent);
					}
					case 3 -> {
						funcTypes = readSection(size, this::readFunctionSectionContent);
					}
					case 4 -> {
						tables = readSection(size, this::readTableSectionContent);
					}
					case 5 -> {
						mems = readSection(size, this::readMemorySectionContent);
					}
					case 6 -> {
						globals = readSection(size, this::readGlobalSectionContent);
					}
					case 7 -> {
						exports = readSection(size, this::readExportSectionContent);
					}
					case 8 -> {
						start = readSection(size, this::readStartSectionContent);
					}
					case 9 -> {
						elems = readSection(size, this::readElementSectionContent);
					}
					case 10 -> {
						codeSec = readSection(size, this::readCodeSectionContent);
					}
					case 11 -> {
						datas = readSection(size, this::readDataSectionContent);
					}
					case 12 -> {
						hasDataCount = true;
						dataCount = readSection(size, this::readDataCountSectionContent);
					}
					default -> throw new ModuleFormatException("malformed section id");
				}

				lastSection = section;
			}

			if(funcTypes.size() != codeSec.size()) {
				throw new ModuleFormatException("function and code section have inconsistent lengths");
			}

			if(hasDataCount && dataCount != datas.size()) {
				throw new ModuleFormatException("data count and data section have inconsistent lengths");
			}

			List<Func> funcs = new ArrayList<>(funcTypes.size());
			for(int i = 0; i < funcTypes.size(); ++i) {
				var code = codeSec.get(i);
				funcs.add(new Func(funcTypes.get(i), code.locals(), code.body()));
			}

			return new Module(
					types,
					funcs,
					tables,
					mems,
					globals,
					elems,
					datas,
					start,
					imports,
					exports
			);
		}
		catch(EOFException ex) {
			throw new ModuleFormatException("unexpected end", ex);
		}
	}

	private <T> T readSection(int expectedSize, ValueReader<T> f) throws IOException, ModuleFormatException {
		sectionStart = offset;
		sectionSize = Integer.toUnsignedLong(expectedSize);
		T res = f.read();
		long actualSize = offset - sectionStart;
		if(actualSize != sectionSize) {
			throw new ModuleFormatException("section size mismatch");
		}

		sectionStart = 0;
		return res;
	}
	
}
