package dev.argon.jawawasm.format.text;

import dev.argon.jawawasm.format.ModuleFormatException;
import dev.argon.jawawasm.format.internal.ParserCallbacks;
import dev.argon.jawawasm.format.internal.generated.TextParserModule;
import dev.argon.jawawasm.runtime.ResultI32;
import dev.argon.jawawasm.runtime.ResultVoid;
import dev.argon.jawawasm.runtime.RuntimeOptions;
import dev.argon.jawawasm.runtime.WasmMemory32;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;

/**
 * Bridges the compiled parser's linear memory and callbacks to Java.
 */
final class TextParser {

	private TextParser() {}

	static byte[] parseWat(String source) throws ModuleFormatException {
		var callbacks = new Callbacks();
		var module = new TextParserModule(() -> new RuntimeOptions(), callbacks);
		callbacks.memory = module.memory();
		var input = source.getBytes(StandardCharsets.UTF_8);
		int address = ResultI32.get(module.jawawasm_allocate(input.length)).item0;
		try {
			callbacks.memory.copyFromArray(address, 0, input.length, input);
			ResultVoid.get(module.jawawasm_parse_wat(0, address, input.length));
		}
		finally {
			ResultVoid.get(module.jawawasm_free(address, input.length));
		}
		if(callbacks.error != null) {
			throw new ModuleFormatException(callbacks.error);
		}
		if(callbacks.bytes == null) {
			throw new ModuleFormatException("Text parser did not report a module");
		}
		return callbacks.bytes;
	}

	private static final class Callbacks extends ParserCallbacks {
		private @Nullable WasmMemory32 memory;
		private byte @Nullable [] bytes;
		private @Nullable String error;

		private byte[] read(int address, int len) {
			if(memory == null) {
				throw new IllegalStateException("Text parser memory is not initialized");
			}
			var bytes = new byte[len];
			memory.copyToArray(address, 0, len, bytes);
			return bytes;
		}

		@Override
		public ResultVoid jawawasm_report_error(int ctx, int message, int len) {
			error = new String(read(message, len), StandardCharsets.UTF_8);
			return ResultVoid.of();
		}

		@Override
		public ResultVoid jawawasm_report_module(int ctx, int id, int idLen, int buff, int buffLen) {
			bytes = read(buff, buffLen);
			return ResultVoid.of();
		}

		@Override
		public ResultVoid jawawasm_report_json(int ctx, int buff, int len) {
			throw new IllegalStateException("Unexpected JSON from WAT parser");
		}
	}
}
