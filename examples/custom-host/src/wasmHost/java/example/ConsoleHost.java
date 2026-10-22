package example;

import dev.argon.jawawasm.runtime.ResultVoid;
import dev.argon.jawawasm.runtime.WasmExport;

/** Host exports must match the names and signatures imported by the Wasm module. */
public final class ConsoleHost {
    @WasmExport(type = WasmExport.ExportType.FUNC)
    public ResultVoid print(int value) {
        System.out.println(value);
        return ResultVoid.of();
    }
}
