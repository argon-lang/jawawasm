package dev.argon.jawawasm.format.tests;

import dev.argon.jawawasm.format.ModuleFormatException;
import dev.argon.jawawasm.format.binary.ModuleReader;
import dev.argon.jawawasm.format.wast.ModuleType;
import dev.argon.jawawasm.format.wast.WastScriptParser;
import dev.argon.jawawasm.format.wast.WastCommand;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class WastScriptParserTest {

	@Test
	public void acceptsUnicodeControlCharactersInNames() throws Exception {
		var exportName = "name" + (char)0x202e;
		var source = "(module (func (export \"" + exportName + "\")))";
		var loaded = new WastScriptParser(new StringReader(source)).parseScript();
		var command = assertInstanceOf(WastCommand.Module.class, loaded.getScript().commands().getFirst());
		var bytes = Objects.requireNonNull(loaded.getFiles().get(command.file().filename()));
		var module = new ModuleReader(new ByteArrayInputStream(bytes)).readModule();
		assertEquals(exportName, module.exports().getFirst().name());
	}

	@Test
	public void readsCommandsAndReferencedModulesInMemory() throws Exception {
		var source = """
			(module $first (func (export "answer") (result i32) i32.const 42))
			(assert_return (invoke $first "answer") (i32.const 42))
			(module $second)
			""";
		var loaded = new WastScriptParser(new StringReader(source), "example.wast").parseScript();
		assertEquals("example.wast", loaded.getScript().sourceFilename());
		assertEquals(3, loaded.getScript().commands().size());
		assertEquals(2, loaded.getFiles().size());
		var first = assertInstanceOf(WastCommand.Module.class, loaded.getScript().commands().getFirst());
		assertEquals("first", first.name());
		var bytes = Objects.requireNonNull(loaded.getFiles().get(first.file().filename()));
		var module = new ModuleReader(new ByteArrayInputStream(bytes)).readModule();
		assertEquals("answer", module.exports().getFirst().name());
		var assertion = assertInstanceOf(WastCommand.AssertReturn.class, loaded.getScript().commands().get(1));
		assertEquals(1, assertion.expected().size());
	}

	@Test
	public void retainsMalformedTextAndBinaryFiles() throws Exception {
		var source = """
			(assert_malformed (module quote "(module") "unexpected end")
			(assert_malformed (module binary "bad") "magic header not detected")
			""";
		var loaded = new WastScriptParser(new StringReader(source)).parseScript();
		var text = assertInstanceOf(WastCommand.AssertMalformed.class, loaded.getScript().commands().get(0));
		assertEquals(ModuleType.TEXT, text.file().moduleType());
		assertNull(text.file().binaryFilename());
		assertTrue(
			new String(Objects.requireNonNull(loaded.getFiles().get(text.file().filename())), StandardCharsets.UTF_8)
				.startsWith("(module")
		);
		var binary = assertInstanceOf(WastCommand.AssertMalformed.class, loaded.getScript().commands().get(1));
		assertEquals(ModuleType.BINARY, binary.file().moduleType());
		assertArrayEquals("bad".getBytes(StandardCharsets.UTF_8), loaded.getFiles().get(binary.file().filename()));
	}

	@Test
	public void retainsQuotedTextAndItsBinaryAlternative() throws Exception {
		var loaded = new WastScriptParser(new StringReader("(module quote \"(module)\")")).parseScript();
		var command = assertInstanceOf(WastCommand.Module.class, loaded.getScript().commands().getFirst());
		assertEquals(ModuleType.TEXT, command.file().moduleType());
		var binaryFilename = Objects.requireNonNull(command.file().binaryFilename());
		assertTrue(loaded.getFiles().containsKey(command.file().filename()));
		var binary = Objects.requireNonNull(loaded.getFiles().get(binaryFilename));
		assertTrue(new ModuleReader(new ByteArrayInputStream(binary)).readModule().funcs().isEmpty());
		assertEquals(2, loaded.getFiles().size());
	}

	@Test
	public void rejectsInvalidScripts() {
		for(var source : new String[]{"(module", "(module) (module (func (call $missing)))"}) {
			assertThrows(
				ModuleFormatException.class,
				() -> new WastScriptParser(new StringReader(source)).parseScript()
			);
		}
	}

	@Test
	public void keepsFilesIndependentAndLeavesInputOpen() throws Exception {
		var reader = new StringReader("(module (func))") {
			@Override
			public void close() {
				fail("ScriptParser must not close its input");
			}
		};
		var first = new WastScriptParser(reader).parseScript();
		var second = new WastScriptParser(new StringReader("(module)")).parseScript();
		var filename = first.getFiles().keySet().iterator().next();
		assertNotSame(first.getFiles().get(filename), second.getFiles().get(filename));
		var bytes = Objects.requireNonNull(first.getFiles().get(filename));
		assertEquals(1, new ModuleReader(new ByteArrayInputStream(bytes)).readModule().funcs().size());
	}
}
