package dev.argon.jawawasm.format.internal;

import dev.argon.jawawasm.runtime.ResultVoid;
import dev.argon.jawawasm.runtime.WasmExport;
import org.jspecify.annotations.NullMarked;

@NullMarked
public abstract class ParserCallbacks {

	@WasmExport(type = WasmExport.ExportType.FUNC)
	public abstract ResultVoid jawawasm_report_error(int ctx, int message, int len);

	@WasmExport(type = WasmExport.ExportType.FUNC)
	public abstract ResultVoid jawawasm_report_module(int ctx, int id, int idLen, int buff, int buffLen);

	@WasmExport(type = WasmExport.ExportType.FUNC)
	public abstract ResultVoid jawawasm_report_json(int ctx, int buff, int len);
}
