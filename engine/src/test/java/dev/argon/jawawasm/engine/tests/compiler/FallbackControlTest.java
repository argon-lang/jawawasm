package dev.argon.jawawasm.engine.tests.compiler;

import dev.argon.jawawasm.engine.compiler.CompilerOptions;
import dev.argon.jawawasm.engine.compiler.ModuleCompiler;
import dev.argon.jawawasm.engine.compiler.WasmClassGenerator;
import dev.argon.jawawasm.engine.compiler.WasmExportRealization;
import dev.argon.jawawasm.engine.validator.ModuleValidator;
import dev.argon.jawawasm.format.binary.ModuleReader;
import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class FallbackControlTest {
	// Select the fallback even though the actual bytecode for these nops is small.
	private static final String FALLBACK_PADDING = "nop ".repeat(1100);

	private static final class GeneratedLoader extends ClassLoader {
		GeneratedLoader(Map<String, byte[]> classes) {
			this.classes = classes;
		}

		private final Map<String, byte[]> classes;

		@Override
		protected Class<?> findClass(String name) throws ClassNotFoundException {
			var bytes = classes.get(name);
			if(bytes == null) {
				throw new ClassNotFoundException(name);
			}
			return defineClass(name, bytes, 0, bytes.length);
		}
	}

	private Object assertResult(String source, Object[] arguments, Object... expected) throws Exception {
		var binary = new java.io.ByteArrayOutputStream();
		new dev.argon.jawawasm.format.text.ModuleReader(new java.io.StringReader(source)).readModuleAsBinary(binary);
		var module = new ModuleReader(new java.io.ByteArrayInputStream(binary.toByteArray())).readModule();
		ModuleValidator.validateModule(module);
		var compiler = new ModuleCompiler(
			new CompilerOptions(ClassHierarchyResolver.defaultResolver(), "test.generated")
		);
		var realization = compiler.enqueueModule(module, "Control", name -> {
			throw new AssertionError("Unexpected import: " + name);
		});
		var classes = new HashMap<String, byte[]>();
		boolean hasFallback = false;
		for(var generator = compiler.dequeueGenerator(); generator != null; generator = compiler.dequeueGenerator()) {
			if(generator instanceof WasmClassGenerator classGenerator) {
				var bytes = classGenerator.generate();
				var descriptor = classGenerator.className().descriptorString();
				classes.put(descriptor.substring(1, descriptor.length() - 1).replace('/', '.'), bytes);
				if(descriptor.contains("_SplitFunc_")) {
					hasFallback = true;
					assertTrue(compiler.classFile().verify(bytes).isEmpty());
					var model = ClassFile.of().parse(bytes);
					for(var field : model.fields()) {
						if(field.fieldName().stringValue().startsWith("state")) {
							assertTrue(field.fieldName().stringValue().matches("state[0-9]+_stack[0-9]+"));
						}
					}
				}
			}
		}
		assertTrue(hasFallback, "The fixture must exercise the fallback compiler");
		var loader = new GeneratedLoader(classes);
		var generatedClass = loader.loadClass("test.generated.Control");
		var constructor = generatedClass.getConstructors()[0];
		// These fixtures have no memories or imports, so no runtime context is needed.
		var instance = constructor.newInstance(new Object[constructor.getParameterCount()]);
		var export = realization.exports().stream().filter(e -> e.exportName().equals("f"))
			.map(e -> (WasmExportRealization.OfInstanceMethod)e).findFirst().orElseThrow();
		var method = Arrays.stream(generatedClass.getMethods()).filter(m -> m.getName().equals(export.methodName()))
			.findFirst().orElseThrow();
		var result = method.invoke(instance, arguments);
		var completed = Objects
			.requireNonNull(method.getReturnType().getMethod("get", method.getReturnType()).invoke(null, result));
		for(int i = 0; i < expected.length; ++i) {
			assertEquals(expected[i], completed.getClass().getField("item" + i).get(completed));
		}
		return instance;
	}

	@Test
	public void returnsMixedValuesAcrossChunkBoundaries() throws Exception {
		assertResult(
			"(module (func (export \"f\") (result i32 i64) i32.const 7 block (result i64) " + "nop ".repeat(51000)
				+ " i64.const 20 end))",
			new Object[]{},
			7,
			20L
		);
	}

	@Test
	public void selectsTypedIfBranches() throws Exception {
		var source = "(module (type $t (func (param i64) (result i64))) (func (export \"f\") (param i32) (result i64) "
			+ FALLBACK_PADDING
			+ "i64.const 20 local.get 0 if (type $t) i64.const 10 i64.add else i64.const 3 i64.add end))";
		assertResult(source, new Object[]{1}, 30L);
		assertResult(source, new Object[]{0}, 23L);
	}

	@Test
	public void loopsAndBranchesToAnEnclosingBlock() throws Exception {
		assertResult(
			"(module (func (export \"f\") (param i32) (result i32) " + FALLBACK_PADDING
				+ "block (result i32) loop local.get 0 i32.const 1 i32.sub local.tee 0 br_if 0 i32.const 42 br 1 end unreachable end))",
			new Object[]{100},
			42
		);
	}

	@Test
	public void normalizesEachBranchTableTarget() throws Exception {
		var source = "(module (func (export \"f\") (param i32) (result i32 i32) " + FALLBACK_PADDING
			+ "i32.const 7 block (result i32) i32.const 99 block (result i32) i32.const 55 local.get 0 br_table 0 1 end i32.add end))";
		for(int selector : new int[]{0, 1, -1, 5}) {
			assertResult(source, new Object[]{selector}, 7, selector == 0 ? 154 : 55);
		}
	}

	@Test
	public void splitsBeforeLargeBranchTable() throws Exception {
		var source = "(module (func (export \"f\") (param i32) (result i32) " + "block (result i32) i32.const 42 "
			+ "nop ".repeat(20000) + "local.get 0 br_table " + "0 ".repeat(12000) + "0 end))";
		for(int selector : new int[]{0, 11999, 12000, -1}) {
			assertResult(source, new Object[]{selector}, 42);
		}
	}

	@Test
	public void returnsDiscardingUnrelatedStackValues() throws Exception {
		assertResult(
			"(module (func (export \"f\") (result i32) " + FALLBACK_PADDING + "i64.const 9 i32.const 42 return))",
			new Object[]{},
			42
		);
	}

	@Test
	public void branchesOnNull() throws Exception {
		assertResult(
			"(module (func (export \"f\") (result i32) " + FALLBACK_PADDING
				+ "block (result i32) i32.const 42 ref.null i31 br_on_null 0 drop i32.const 1 i32.add end))",
			new Object[]{},
			42
		);
	}

	@Test
	public void catchesTagPayloadAcrossChunkBoundaries() throws Exception {
		assertResult(
			"(module (tag $e (param i32)) (func (export \"f\") (result i64 i32) " + FALLBACK_PADDING
				+ "i64.const 17 block (result i32) try_table (result i32) (catch $e 0) " + "nop ".repeat(31000)
				+ " i32.const 42 throw $e end end))",
			new Object[]{},
			17L,
			42
		);
	}

	@Test
	public void callsDirectFunctions() throws Exception {
		assertResult(
			"(module (func $g (param i32) (result i32) local.get 0 i32.const 1 i32.add) "
				+ "(func (export \"f\") (result i32) " + FALLBACK_PADDING + "i32.const 41 call $g))",
			new Object[]{},
			42
		);
	}

	@Test
	public void tailCallsThroughTheExistingTrampoline() throws Exception {
		assertResult(
			"(module (func $f (export \"f\") (param i32) (result i32) " + FALLBACK_PADDING
				+ "local.get 0 i32.eqz if i32.const 42 return end local.get 0 i32.const 1 i32.sub return_call $f))",
			new Object[]{10000},
			42
		);
	}

	@Test
	public void preservesLoopParametersAndTheOuterStack() throws Exception {
		assertResult(
			"(module (type $loop (func (param i64) (result i64))) (func (export \"f\") (param i32) (result i32 i64) "
				+ FALLBACK_PADDING + "i32.const 7 i64.const 20 loop (type $loop) i64.const 1 i64.add "
				+ "local.get 0 i32.const 1 i32.sub local.tee 0 br_if 0 end))",
			new Object[]{3},
			7,
			23L
		);
	}

	@Test
	public void branchesOnReferencesAndCasts() throws Exception {
		assertResult(
			"(module (func (export \"f\") (result i32) " + FALLBACK_PADDING
				+ "block (result (ref null i31)) i32.const 42 ref.i31 br_on_non_null 0 i32.const 7 ref.i31 end i31.get_s))",
			new Object[]{},
			42
		);
		for(int selector : new int[]{0, 1}) {
			assertResult(
				"(module (func (export \"f\") (param i32) (result i32) " + FALLBACK_PADDING
					+ "block (result (ref null i31)) local.get 0 if (result anyref) i32.const 42 ref.i31 else ref.null any end "
					+ "br_on_cast 0 (ref null any) (ref i31) drop i32.const 7 ref.i31 end i31.get_s))",
				new Object[]{selector},
				selector == 0 ? 7 : 42
			);
			assertResult(
				"(module (func (export \"f\") (param i32) (result i32) " + FALLBACK_PADDING
					+ "block (result anyref) local.get 0 if (result anyref) i32.const 42 ref.i31 else ref.null any end "
					+ "br_on_cast_fail 0 (ref null any) (ref i31) i31.get_s drop i32.const 7 ref.i31 end ref.is_null))",
				new Object[]{selector},
				selector == 0 ? 1 : 0
			);
		}
	}

	@Test
	public void callsReferencesAndTableEntries() throws Exception {
		for(var operation : new String[]{"call_ref", "return_call_ref"}) {
			assertResult(
				"(module (type $t (func (result i32))) (elem declare func $g) (func $g (type $t) i32.const 42) "
					+ "(func (export \"f\") (result i32) " + FALLBACK_PADDING + "ref.func $g " + operation + " $t))",
				new Object[]{},
				42
			);
		}
		for(var operation : new String[]{"call_indirect", "return_call_indirect"}) {
			assertResult(
				"(module (type $t (func (result i32))) (table 1 funcref) (elem (i32.const 0) $g) "
					+ "(func $g (type $t) i32.const 42) (func (export \"f\") (result i32) " + FALLBACK_PADDING
					+ "i32.const 0 " + operation + " (type $t)))",
				new Object[]{},
				42
			);
		}
	}

	@Test
	public void givesInnerCatchRegionsPrecedence() throws Exception {
		assertResult(
			"(module (tag $e (param i32)) (func (export \"f\") (result i32) " + FALLBACK_PADDING
				+ "block (result i32) try_table (result i32) (catch $e 0) block (result i32) "
				+ "try_table (result i32) (catch $e 0) i32.const 42 throw $e end end i32.const 1 i32.add end end))",
			new Object[]{},
			43
		);
	}

	@Test
	public void catchesAllWithAndWithoutAnExceptionReference() throws Exception {
		assertResult(
			"(module (tag $e) (func (export \"f\") (result i32) " + FALLBACK_PADDING
				+ "block try_table (catch_all 0) throw $e end end i32.const 42))",
			new Object[]{},
			42
		);
		assertResult(
			"(module (tag $e) (func (export \"f\") (result i32) " + FALLBACK_PADDING
				+ "block (result exnref) try_table (result exnref) (catch_all_ref 0) throw $e end end ref.is_null))",
			new Object[]{},
			0
		);
	}

	@Test
	public void clearsPreciselyTypedReferenceStashesAfterLoading() throws Exception {
		var referenceType = Class.forName("dev.argon.jawawasm.runtime.I31");
		var value = referenceType.getConstructor(int.class).newInstance(42);
		var module = assertResult(
			"(module (func (export \"f\") (param (ref null i31)) (result i32) "
				+ "block (result (ref null i31)) local.get 0 " + "nop ".repeat(51000) + "end i31.get_s))",
			new Object[]{value},
			42
		);
		var helperClass = Objects.requireNonNull(module.getClass().getClassLoader())
			.loadClass("test.generated.Control_SplitFunc_0");
		var helper = helperClass.getConstructors()[0].newInstance(value, module);
		helperClass.getMethod("execute").invoke(helper);
		boolean checkedReference = false;
		for(var field : helperClass.getDeclaredFields()) {
			if(field.getName().startsWith("state") && !field.getType().isPrimitive()) {
				checkedReference = true;
				assertEquals(referenceType, field.getType());
				field.setAccessible(true);
				assertNull(field.get(helper));
			}
		}
		assertTrue(checkedReference);
	}

	@Test
	public void catchesAndRethrowsAnExceptionReference() throws Exception {
		assertResult(
			"(module (tag $e (param i32)) (func (export \"f\") (result i32) " + FALLBACK_PADDING
				+ "block (result i32) try_table (result i32) (catch $e 0) block (result i32 exnref) "
				+ "try_table (result i32 exnref) (catch_ref $e 0) i32.const 42 throw $e end end throw_ref end end))",
			new Object[]{},
			42
		);
	}

	@Test
	public void propagatesUnreachableTrapsPastWasmCatches() throws Exception {
		var exception = assertThrows(
			java.lang.reflect.InvocationTargetException.class,
			() -> assertResult(
				"(module (func (export \"f\") (result i32) " + FALLBACK_PADDING
					+ "block try_table (catch_all 0) unreachable end end i32.const 42))",
				new Object[]{},
				42
			)
		);
		assertEquals(
			"dev.argon.jawawasm.runtime.UnreachableTrap",
			Objects.requireNonNull(exception.getCause()).getClass().getName()
		);
	}

}
