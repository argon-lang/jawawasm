package dev.argon.jawawasm.app;

import dev.argon.jawawasm.runtime.*;
import org.jspecify.annotations.Nullable;

import java.io.PrintWriter;


public final class SpecTestModuleInstance extends WasmModule {
	SpecTestModuleInstance(MemoryAllocator allocator, PrintWriter output) {
		this.output = output;

		memory = WasmMemory.create(allocator, AddrType.I32, 1, 2L);
	}

	private final WasmMemory memory;
	private final WasmTable<@Nullable WasmFunction> table = WasmTable.<@Nullable WasmFunction>create(10, 20L, null);
	private final WasmTable<@Nullable WasmFunction> table64 = WasmTable.<@Nullable WasmFunction>create(10, 20L, null);
	private final PrintWriter output;

	@WasmExport(type = WasmExport.ExportType.GLOBAL)
	public int global_i32() {
		return 666;
	}

	@WasmExport(type = WasmExport.ExportType.GLOBAL)
	public long global_i64() {
		return 666;
	}

	@WasmExport(type = WasmExport.ExportType.GLOBAL)
	public float global_f32() {
		return 666.6f;
	}

	@WasmExport(type = WasmExport.ExportType.GLOBAL)
	public double global_f64() {
		return 666.6;
	}

	@WasmExport(type = WasmExport.ExportType.MEMORY)
	@SizeLimits(addressType = AddrType.I32, min = 1, max = 2)
	public WasmMemory memory() {
		return memory;
	}

	@WasmExport(type = WasmExport.ExportType.TABLE)
	@SizeLimits(addressType = AddrType.I32, min = 10, max = 20)
	public WasmTable<@Nullable WasmFunction> table() {
		return table;
	}

	@WasmExport(type = WasmExport.ExportType.TABLE)
	@SizeLimits(addressType = AddrType.I64, min = 10, max = 20)
	public WasmTable<@Nullable WasmFunction> table64() {
		return table64;
	}

	@WasmExport(type = WasmExport.ExportType.FUNC)
	public ResultVoid print() {
		output.println();
		return ResultVoid.of();
	}

	@WasmExport(type = WasmExport.ExportType.FUNC)
	public ResultVoid print_i32(int value) {
		output.println(value);
		return ResultVoid.of();
	}

	@WasmExport(type = WasmExport.ExportType.FUNC)
	public ResultVoid print_i64(long value) {
		output.println(value);
		return ResultVoid.of();
	}

	@WasmExport(type = WasmExport.ExportType.FUNC)
	public ResultVoid print_f32(float value) {
		output.println(value);
		return ResultVoid.of();
	}

	@WasmExport(type = WasmExport.ExportType.FUNC)
	public ResultVoid print_f64(double value) {
		output.println(value);
		return ResultVoid.of();
	}

	@WasmExport(type = WasmExport.ExportType.FUNC)
	public ResultVoid print_i32_f32(int a, float b) {
		output.println(a);
		output.println(b);
		return ResultVoid.of();
	}

	@WasmExport(type = WasmExport.ExportType.FUNC)
	public ResultVoid print_f64_f64(double a, double b) {
		output.println(a);
		output.println(b);
		return ResultVoid.of();
	}


}
