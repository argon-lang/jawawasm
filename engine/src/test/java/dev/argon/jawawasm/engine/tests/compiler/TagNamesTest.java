package dev.argon.jawawasm.engine.tests.compiler;

import org.junit.jupiter.api.Test;

import java.lang.classfile.Attributes;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TagNamesTest extends CompilerTestBase {
	@Test
	public void usesSanitizedTagNamesForExceptionClasses() throws Exception {
		var classes = compile("tag-names.wat");
		var tags = classes.stream()
			.filter(c -> c.thisClass().asInternalName().contains("$Tag"))
			.toList();
		assertEquals(List.of("test/generated/LargeModule$Tag0_error_name", "test/generated/LargeModule$Tag1_same_name",
			"test/generated/LargeModule$Tag2_same_name", "test/generated/LargeModule$Tag3"),
			tags.stream().map(c -> c.thisClass().asInternalName()).toList());
		for(var tag : tags) {
			assertEquals("dev/argon/jawawasm/runtime/WebAssemblyException", tag.superclass().orElseThrow().asInternalName());
			var inner = tag.findAttribute(Attributes.innerClasses()).orElseThrow().classes().getFirst();
			assertEquals(tag.thisClass().asInternalName(), inner.innerClass().asInternalName());
			var className = tag.thisClass().asInternalName();
			assertEquals(className.substring(className.lastIndexOf('$') + 1), inner.innerName().orElseThrow().stringValue());
		}
	}
}
