package dev.argon.jawawasm.format.text;

import dev.argon.jawawasm.format.ModuleFormatException;
import dev.argon.jawawasm.format.modules.Module;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.util.Objects;

/**
 * A reader for WebAssembly text modules.
 */
public class ModuleReader {

	/**
	 * Creates a text module reader.
	 *
	 * @param reader
	 *            The reader containing the WebAssembly text format.
	 */
	public ModuleReader(Reader reader) {
		this.reader = Objects.requireNonNull(reader);
	}

	private final Reader reader;

	/**
	 * Reads a WebAssembly text module.
	 *
	 * @return The parsed module.
	 * @throws IOException
	 *             when reading the text fails.
	 * @throws ModuleFormatException
	 *             when the text is invalid.
	 */
	public Module readModule() throws IOException, ModuleFormatException {
		return new dev.argon.jawawasm.format.binary.ModuleReader(new ByteArrayInputStream(readBinary())).readModule();
	}

	/**
	 * Reads a WebAssembly text module and writes its binary format to a stream.
	 * Neither the reader nor the output stream is closed.
	 *
	 * @param output
	 *            The stream to write the binary module to.
	 * @throws IOException
	 *             when reading the text or writing the binary fails.
	 * @throws ModuleFormatException
	 *             when the text is invalid.
	 */
	public void readModuleAsBinary(OutputStream output) throws IOException, ModuleFormatException {
		Objects.requireNonNull(output);
		output.write(readBinary());
	}

	private byte[] readBinary() throws IOException, ModuleFormatException {
		var source = new StringWriter();
		reader.transferTo(source);
		return TextParser.parseWat(source.toString());
	}
}
