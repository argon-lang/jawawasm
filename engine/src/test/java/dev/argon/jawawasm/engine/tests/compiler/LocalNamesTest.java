package dev.argon.jawawasm.engine.tests.compiler;

import org.junit.jupiter.api.Test;

import java.lang.classfile.Attributes;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LocalNamesTest extends CompilerTestBase {
	@Test
	public void emitsLocalNamesWithJvmSlotsAndScopes() throws Exception {
		var methods = compile("local-names.wat").stream()
			.filter(c -> c.thisClass().asInternalName().contains("_Funcs_")).flatMap(c -> c.methods().stream())
			.toList();
		var named = methods.stream().filter(m -> m.methodName().stringValue().equals("func0_named")).toList();
		assertEquals(2, named.size());
		for(var method : named) {
			var code = method.findAttribute(Attributes.code()).orElseThrow();
			var locals = code.findAttribute(Attributes.localVariableTable()).orElseThrow().localVariables();
			boolean thunk = method.methodType().stringValue().startsWith("(L");
			assertEquals(
				thunk ? List.of("wide", "value-name") : List.of("wide", "value-name", "double-value", "counter"),
				locals.stream().map(l -> l.name().stringValue()).toList()
			);
			assertEquals(thunk ? List.of(1, 3) : List.of(0, 2, 4, 6), locals.stream().map(l -> l.slot()).toList());
			assertEquals(
				thunk ? List.of("J", "I") : List.of("J", "I", "D", "I"),
				locals.stream().map(l -> l.type().stringValue()).toList()
			);
			for(int i = 0; i < locals.size(); ++i) {
				var local = locals.get(i);
				if(i < 2) {
					assertEquals(0, local.startPc());
				}
				else {
					assertTrue(local.startPc() > 0);
				}
				assertEquals(code.codeLength(), local.startPc() + local.length());
			}
		}
		for(var method : methods.stream().filter(m -> m.methodName().stringValue().equals("func1")).toList()) {
			assertTrue(method.code().orElseThrow().findAttribute(Attributes.localVariableTable()).isEmpty());
		}
	}
}
