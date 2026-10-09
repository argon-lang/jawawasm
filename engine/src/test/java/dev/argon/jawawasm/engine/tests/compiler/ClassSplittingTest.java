package dev.argon.jawawasm.engine.tests.compiler;

import org.junit.jupiter.api.Test;

import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public class ClassSplittingTest extends CompilerTestBase {
	@Test
	public void splitsFunctionsAcrossClassfiles() throws Exception {
		var classes = compile("large-functions.wat");
		var functionClasses = classes.stream().filter(c -> c.thisClass().asInternalName().contains("_Funcs_")).toList();
		assertEquals(3, functionClasses.size());
		var functionNames = new HashSet<String>();
		var counts = new ArrayList<Integer>();
		for(var cls : functionClasses) {
			var functions = cls.methods().stream()
				.filter(m -> m.methodName().stringValue().matches("func[0-9]+_f[0-9]+")).toList();
			counts.add(functions.size());
			for(var method : functions) {
				assertTrue(functionNames.add(method.methodName().stringValue()), "Duplicate function");
				assertTrue(method.code().isPresent());
			}
		}
		assertEquals(List.of(5, 100, 100), counts.stream().sorted().toList());
		assertEquals(
			new HashSet<>(IntStream.range(0, 205).mapToObj(i -> "func" + i + "_f" + i).toList()),
			functionNames
		);
	}

	@Test
	public void splitsElementInitializersAcrossClassfiles() throws Exception {
		var classes = compile("large-elements.wat");
		var initializerClasses = classes.stream().filter(c -> c.thisClass().asInternalName().contains("_ElemsInit_"))
			.toList();
		assertEquals(3, initializerClasses.size());
		var counts = new ArrayList<Long>();
		for(var cls : initializerClasses) {
			assertEquals(1, cls.methods().size());
			var method = cls.methods().getFirst();
			assertEquals("init", method.methodName().stringValue());
			counts.add(
				method.code().orElseThrow().elementStream()
					.filter(
						e -> e instanceof ArrayStoreInstruction instruction && instruction.opcode() == Opcode.AASTORE
					).count()
			);
		}
		assertEquals(List.of(5L, 500L, 500L), counts.stream().sorted().toList());
	}
}
