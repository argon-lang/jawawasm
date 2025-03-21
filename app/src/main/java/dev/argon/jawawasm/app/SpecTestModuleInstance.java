package dev.argon.jawawasm.app;

import dev.argon.jawawasm.runtime.*;
import org.jspecify.annotations.Nullable;

import java.io.PrintWriter;


public final class SpecTestModuleInstance extends WasmModule {
	SpecTestModuleInstance(PrintWriter output) {
		this.output = output;
	}

	private final WasmTable<@Nullable WasmFunction> table = WasmTable.<@Nullable WasmFunction>create(10, 20L, null);
	private final WasmTable<@Nullable WasmFunction> table64 = WasmTable.<@Nullable WasmFunction>create(10, 20L, null);
	private final PrintWriter output;

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
