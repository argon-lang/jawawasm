package jvmwasm.engine;

import jvmwasm.format.modules.Data;
import jvmwasm.format.types.MemType;

public sealed interface WasmMemory extends WasmMemoryNoResize, WasmExport permits WasmMemoryMeta {

	MemType type();

	int grow(int pages) throws Throwable;

	public static WasmMemory create(Engine engine, MemType memType) {
		return new WasmMemoryMeta(engine, memType.limits().max(), engine.allocateMemory(memType.limits().min()));
	}

	public static void fill(int d, byte val, int n, WasmMemory memory) throws Throwable {
		if(!Util.sumInRange(d, n, memory.byteSize())) {
			throw new IndexOutOfBoundsException();
		}

		while(n != 0) {
			memory.storeI8(d, val);
			++d;
			--n;
		}
	}

	public static void copy(int d, int s, int n, WasmMemory memory) throws Throwable {
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

	public static void init(int d, int s, int n, WasmMemory memory, Data data) throws Throwable {
		memory.init(d, s, n, data);
	}
}
