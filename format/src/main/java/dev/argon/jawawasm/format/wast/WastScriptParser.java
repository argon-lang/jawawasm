package dev.argon.jawawasm.format.wast;

import com.google.common.collect.ImmutableMap;
import dev.argon.jawawasm.format.ModuleFormatException;
import dev.argon.jawawasm.format.internal.ParserCallbacks;
import dev.argon.jawawasm.format.internal.generated.TextParserModule;
import dev.argon.jawawasm.runtime.ResultI32;
import dev.argon.jawawasm.runtime.ResultVoid;
import dev.argon.jawawasm.runtime.RuntimeOptions;
import dev.argon.jawawasm.runtime.WasmMemory32;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.guava.GuavaModule;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Parses WAST scripts using the AOT-compiled Rust text parser.
 */
public final class WastScriptParser {

	private static final ObjectMapper MAPPER = JsonMapper.builder().addModule(new GuavaModule()).build();

	/**
	 * Creates a script parser with the default source filename, {@code input.wast}.
	 *
	 * @param reader
	 *            The input reader, which remains owned by the caller.
	 */
	public WastScriptParser(Reader reader) {
		this(reader, "input.wast");
	}

	/**
	 * Creates a script parser with a source filename for diagnostics.
	 *
	 * @param reader
	 *            The input reader, which remains owned by the caller.
	 * @param sourceFilename
	 *            The source filename recorded in the parsed script.
	 */
	public WastScriptParser(Reader reader, String sourceFilename) {
		this.reader = Objects.requireNonNull(reader);
		this.sourceFilename = Objects.requireNonNull(sourceFilename);
	}

	private final Reader reader;
	private final String sourceFilename;

	/**
	 * Parses a script, retaining all referenced module files in memory.
	 *
	 * @return The commands and their generated files.
	 * @throws IOException
	 *             If reading the input fails.
	 * @throws ModuleFormatException
	 *             If the script cannot be parsed or converted.
	 */
	public WastScriptLoaded parseScript() throws IOException, ModuleFormatException {
		var source = new StringWriter();
		reader.transferTo(source);
		var input = source.toString().getBytes(StandardCharsets.UTF_8);
		var callbacks = new Callbacks();
		var module = new TextParserModule(() -> new RuntimeOptions(), callbacks);
		var memory = module.memory();
		callbacks.memory = memory;
		int address = ResultI32.get(module.jawawasm_allocate(input.length)).item0;
		try {
			memory.copyFromArray(address, 0, input.length, input);
			ResultVoid.get(module.jawawasm_parse_wast(0, address, input.length));
		}
		finally {
			ResultVoid.get(module.jawawasm_free(address, input.length));
		}
		if(callbacks.error != null) {
			throw new ModuleFormatException(callbacks.error);
		}
		if(callbacks.json == null) {
			throw new ModuleFormatException("Script parser did not report JSON");
		}
		var script = MAPPER.readValue(callbacks.json, WastScript.class);
		return new WastScriptLoaded(
			ImmutableMap.copyOf(callbacks.files),
			new WastScript(sourceFilename, script.commands())
		);
	}

	private static final class Callbacks extends ParserCallbacks {
		private @Nullable WasmMemory32 memory;
		private final Map<String, byte[]> files = new LinkedHashMap<>();
		private @Nullable String error;
		private byte @Nullable [] json;

		private byte[] read(int address, int len) {
			if(memory == null) {
				throw new IllegalStateException("Script parser memory is not initialized");
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
			files.put(new String(read(id, idLen), StandardCharsets.UTF_8), read(buff, buffLen));
			return ResultVoid.of();
		}

		@Override
		public ResultVoid jawawasm_report_json(int ctx, int buff, int len) {
			json = read(buff, len);
			return ResultVoid.of();
		}
	}
}
