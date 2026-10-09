package dev.argon.jawawasm.format.tests;

import dev.argon.jawawasm.format.ModuleFormatException;
import dev.argon.jawawasm.format.text.ModuleReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class TextModuleReaderTest {

	@Test
	public void readsModuleWithFunctionAndUnicodeExport() throws Exception {
		var reader = new ModuleReader(new StringReader("""
			(module $test
			  (func (export "réponse") (result i32)
			    i32.const 42))
			"""));
		var module = reader.readModule();
		assertEquals(1, module.funcs().size());
		assertEquals("réponse", module.exports().getFirst().name());
	}

	@Test
	public void rejectsSyntaxAndNameResolutionErrors() {
		for(var source : new String[]{"(module", "(module (func (call $missing)))", ""}) {
			var error = assertThrows(
				ModuleFormatException.class,
				() -> new ModuleReader(new StringReader(source)).readModule()
			);
			assertFalse(Objects.requireNonNull(error.getMessage()).isEmpty());
		}
	}

	@Test
	public void readsInputLargerThanInitialMemory() throws Exception {
		var source = "(module (;" + "x".repeat(2 * 1024 * 1024) + ";))";
		var module = new ModuleReader(new StringReader(source)).readModule();
		assertTrue(module.funcs().isEmpty());
	}

	@Test
	public void propagatesReadErrors() {
		var failure = new IOException("input failed");
		var reader = new Reader() {
			@Override
			public int read(char[] buff, int offset, int len) throws IOException {
				throw failure;
			}

			@Override
			public void close() {}
		};
		assertSame(failure, assertThrows(IOException.class, () -> new ModuleReader(reader).readModule()));
	}

	@Test
	public void leavesReaderOpenAndKeepsParsesIndependent() throws Exception {
		for(int i = 0; i < 3; ++i) {
			var reader = new StringReader("(module)") {
				@Override
				public void close() {
					fail("ModuleReader must not close its input");
				}
			};
			assertTrue(new ModuleReader(reader).readModule().exports().isEmpty());
		}
	}
}
