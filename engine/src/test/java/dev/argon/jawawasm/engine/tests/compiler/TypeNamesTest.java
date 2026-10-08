package dev.argon.jawawasm.engine.tests.compiler;

import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassModel;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TypeNamesTest extends CompilerTestBase {
	@Test
	public void usesTypeAndFieldNames() throws Exception {
		var classes = compile("type-names.wat");
		var record = findClass(classes, "_Record_name");
		assertEquals(List.of("field0_value_name", "field1"), record.fields().stream().map(f -> f.fieldName().stringValue()).toList());
		assertTrue(findClass(classes, "_Numbers_array").thisClass().asInternalName().contains("/Array"));
		assertTrue(findClass(classes, "_Callback_type").thisClass().asInternalName().contains("/Func"));
		assertEquals(List.of("field0_base_field"), findClass(classes, "_Base_record$Impl").fields().stream().map(f -> f.fieldName().stringValue()).toList());
		assertEquals(List.of("field0_child_field", "field1_extra_field"), findClass(classes, "_Child_record$Impl").fields().stream().map(f -> f.fieldName().stringValue()).toList());

		var loader = new ClassLoader(getClass().getClassLoader()) {
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				var model = classes.stream().filter(c -> c.thisClass().asInternalName().replace('/', '.').equals(name)).findFirst().orElseThrow(() -> new ClassNotFoundException(name));
				var bytes = java.lang.classfile.ClassFile.of().transformClass(model, java.lang.classfile.ClassTransform.ACCEPT_ALL);
				return defineClass(name, bytes, 0, bytes.length);
			}
		};
		var recordClass = loader.loadClass(record.thisClass().asInternalName().replace('/', '.'));
		var instance = recordClass.getMethod("create", int.class, long.class).invoke(null, 7, 9L);
		assertEquals(7, recordClass.getMethod("get0").invoke(instance));
		recordClass.getMethod("set0", int.class).invoke(instance, 11);
		assertEquals(11, recordClass.getMethod("get0").invoke(instance));
		assertEquals(9L, recordClass.getMethod("get1").invoke(instance));
	}

	private static ClassModel findClass(List<ClassModel> classes, String suffix) {
		return classes.stream().filter(c -> c.thisClass().asInternalName().endsWith(suffix)).findFirst().orElseThrow();
	}
}
