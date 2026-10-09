package dev.argon.jawawasm.app.tests;

import dev.argon.jawawasm.app.ScriptInterpreter;
import dev.argon.jawawasm.app.ScriptReflectionExecutor;
import dev.argon.jawawasm.format.wast.WastScriptParser;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

public class InMemoryScriptTest {

	private static final String SCRIPT = """
		(module $first (func (export "answer") (result i32) i32.const 42))
		(register "first" $first)
		(module (import "first" "answer" (func $answer (result i32)))
		  (func (export "answer") (result i32) call $answer))
		(assert_return (invoke "answer") (i32.const 42))
		(module quote "(module (func (export \\"answer\\") (result i32) i32.const 7))")
		(assert_return (invoke "answer") (i32.const 7))
		""";

	@Test
	public void interpretsInMemoryScript() throws Exception {
		var loaded = new WastScriptParser(new StringReader(SCRIPT)).parseScript();
		try(var executor = new ScriptInterpreter(new PrintWriter(new StringWriter()))) {
			executor.initialize();
			executor.executeScript(loaded);
		}
	}

	@Test
	public void compilesAndExecutesInMemoryScript() throws Exception {
		var loaded = new WastScriptParser(new StringReader(SCRIPT)).parseScript();
		try(var executor = new ScriptReflectionExecutor("test.inmemory", new PrintWriter(new StringWriter()))) {
			executor.initialize();
			executor.executeScript(loaded);
		}
	}
}
