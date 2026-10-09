package dev.argon.jawawasm.engine.interpreter;

import dev.argon.jawawasm.runtime.*;

/** Dispatches interpreter memory operations to the concrete address type. */
final class MemoryAccess {
	private MemoryAccess() {}

	private static int address32(long address) {
		if(address < 0 || address > 0xFFFF_FFFFL) {
			throw new IndexOutOfBoundsException();
		}
		return (int)address;
	}

	static AddrType addressType(WasmMemory memory) {
		return switch(memory) {
			case WasmMemory32 ignored -> AddrType.I32;
			case WasmMemory64 ignored -> AddrType.I64;
		};
	}

	static long pageSize(WasmMemory memory) {
		return switch(memory) {
			case WasmMemory32 mem -> mem.pageSize();
			case WasmMemory64 mem -> mem.pageSize();
		};
	}

	static long grow(WasmMemory memory, long pages) {
		return switch(memory) {
			case WasmMemory32 mem -> mem.grow((int)pages);
			case WasmMemory64 mem -> mem.grow(pages);
		};
	}

	static byte loadI8(WasmMemory memory, long address) {
		return switch(memory) {
			case WasmMemory32 mem -> mem.loadI8(address32(address));
			case WasmMemory64 mem -> mem.loadI8(address);
		};
	}

	static void storeI8(WasmMemory memory, long address, byte value) {
		switch(memory) {
			case WasmMemory32 mem -> mem.storeI8(address32(address), value);
			case WasmMemory64 mem -> mem.storeI8(address, value);
		}
	}

	static short loadI16(WasmMemory memory, long address) {
		return switch(memory) {
			case WasmMemory32 mem -> mem.loadI16(address32(address));
			case WasmMemory64 mem -> mem.loadI16(address);
		};
	}

	static void storeI16(WasmMemory memory, long address, short value) {
		switch(memory) {
			case WasmMemory32 mem -> mem.storeI16(address32(address), value);
			case WasmMemory64 mem -> mem.storeI16(address, value);
		}
	}

	static int loadI32(WasmMemory memory, long address) {
		return switch(memory) {
			case WasmMemory32 mem -> mem.loadI32(address32(address));
			case WasmMemory64 mem -> mem.loadI32(address);
		};
	}

	static void storeI32(WasmMemory memory, long address, int value) {
		switch(memory) {
			case WasmMemory32 mem -> mem.storeI32(address32(address), value);
			case WasmMemory64 mem -> mem.storeI32(address, value);
		}
	}

	static long loadI64(WasmMemory memory, long address) {
		return switch(memory) {
			case WasmMemory32 mem -> mem.loadI64(address32(address));
			case WasmMemory64 mem -> mem.loadI64(address);
		};
	}

	static void storeI64(WasmMemory memory, long address, long value) {
		switch(memory) {
			case WasmMemory32 mem -> mem.storeI64(address32(address), value);
			case WasmMemory64 mem -> mem.storeI64(address, value);
		}
	}

	static float loadF32(WasmMemory memory, long address) {
		return switch(memory) {
			case WasmMemory32 mem -> mem.loadF32(address32(address));
			case WasmMemory64 mem -> mem.loadF32(address);
		};
	}

	static void storeF32(WasmMemory memory, long address, float value) {
		switch(memory) {
			case WasmMemory32 mem -> mem.storeF32(address32(address), value);
			case WasmMemory64 mem -> mem.storeF32(address, value);
		}
	}

	static double loadF64(WasmMemory memory, long address) {
		return switch(memory) {
			case WasmMemory32 mem -> mem.loadF64(address32(address));
			case WasmMemory64 mem -> mem.loadF64(address);
		};
	}

	static void storeF64(WasmMemory memory, long address, double value) {
		switch(memory) {
			case WasmMemory32 mem -> mem.storeF64(address32(address), value);
			case WasmMemory64 mem -> mem.storeF64(address, value);
		}
	}

	static V128 loadV128(WasmMemory memory, long address) {
		return switch(memory) {
			case WasmMemory32 mem -> mem.loadV128(address32(address));
			case WasmMemory64 mem -> mem.loadV128(address);
		};
	}

	static void storeV128(WasmMemory memory, long address, V128 value) {
		switch(memory) {
			case WasmMemory32 mem -> mem.storeV128(address32(address), value);
			case WasmMemory64 mem -> mem.storeV128(address, value);
		}
	}

	static void fill(WasmMemory memory, long d, byte val, long n) {
		switch(memory) {
			case WasmMemory32 mem -> mem.fill(address32(d), val, address32(n));
			case WasmMemory64 mem -> mem.fill(d, val, n);
		}
	}

	static void copy(WasmMemory dstMemory, WasmMemory srcMemory, long d, long s, long n) {
		switch(dstMemory) {
			case WasmMemory32 dst -> {
				switch(srcMemory) {
					case WasmMemory32 src -> WasmMemory32.copy(address32(d), address32(s), address32(n), dst, src);
					case WasmMemory64 src -> WasmMemory32.copy(address32(d), s, address32(n), dst, src);
				}
			}
			case WasmMemory64 dst -> {
				switch(srcMemory) {
					case WasmMemory32 src -> WasmMemory64.copy(d, address32(s), address32(n), dst, src);
					case WasmMemory64 src -> WasmMemory64.copy(d, s, n, dst, src);
				}
			}
		}
	}

	static void copyFromArray(WasmMemory memory, long address, int offset, int length, byte[] data) {
		switch(memory) {
			case WasmMemory32 mem -> mem.copyFromArray(address32(address), offset, length, data);
			case WasmMemory64 mem -> mem.copyFromArray(address, offset, length, data);
		}
	}
}
