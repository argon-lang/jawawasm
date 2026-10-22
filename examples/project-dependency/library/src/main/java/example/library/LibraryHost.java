package example.library;

import dev.argon.jawawasm.runtime.ResultI32;
import dev.argon.jawawasm.runtime.WasmExport;

/** Exposes the separately compiled library through the Java host import API. */
public final class LibraryHost {
    private final Library library;

    public LibraryHost(Library library) {
        this.library = library;
    }

    @WasmExport(type = WasmExport.ExportType.FUNC)
    public ResultI32 answer() {
        return library.answer();
    }
}
