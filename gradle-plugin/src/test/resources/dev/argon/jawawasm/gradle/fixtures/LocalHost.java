package example;

import dev.argon.jawawasm.runtime.ResultVoid;
import dev.argon.jawawasm.runtime.WasmExport;

public abstract class LocalHost {
	@WasmExport(type = WasmExport.ExportType.FUNC)
	public abstract ResultVoid f();
}
