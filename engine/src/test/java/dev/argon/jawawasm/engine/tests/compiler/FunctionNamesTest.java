package dev.argon.jawawasm.engine.tests.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FunctionNamesTest extends CompilerTestBase {
	@Test
	public void sanitizesFunctionNames() throws Exception {
		var names = compile("function-names.wat").stream()
			.filter(c -> c.thisClass().asInternalName().contains("_Funcs_")).flatMap(c -> c.methods().stream())
			.map(m -> m.methodName().stringValue()).filter(name -> name.startsWith("func")).toList();
		assertEquals(
			List.of(
				"func0_hello_World42",
				"func1__leading_trailing_",
				"func2_same_name",
				"func3_same_name",
				"func4",
				"func5_caller"
			),
			names
		);
	}
}
