package dev.argon.jawawasm.engine.tests.compiler;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dev.argon.jawawasm.engine.compiler.*;
import dev.argon.jawawasm.engine.validator.ModuleValidator;
import dev.argon.jawawasm.format.instructions.*;
import dev.argon.jawawasm.format.modules.*;
import dev.argon.jawawasm.format.modules.Module;
import dev.argon.jawawasm.format.types.*;
import dev.argon.jawawasm.runtime.V128;
import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.util.HashMap;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class V128LocalInitializationTest {
	@Test
	public void initializesVectorLocalsInNormalAndFallbackFunctions() throws Exception {
		var returnValues = ImmutableList.<Instr>of(
			new VariableInstr.Local_Get(new LocalIdx(0)),
			new VariableInstr.Local_Set(new LocalIdx(0)),
			new VariableInstr.Local_Get(new LocalIdx(0)),
			new VariableInstr.Local_Get(new LocalIdx(2)),
			new VariableInstr.Local_Get(new LocalIdx(3))
		);
		var largeBody = ImmutableList.<Instr>builder();
		for(int i = 0; i < 31000; ++i) largeBody.add(new ControlInstr.Nop());
		largeBody.addAll(returnValues);
		var locals = ImmutableList.<ValType>of(NumType.I64, VecType.V128, VecType.V128);
		var module = new Module(
			ImmutableList.of(new RecursiveType(ImmutableList.of(new SubType(true, ImmutableList.of(),
				new FuncType(new ResultType(ImmutableList.of(VecType.V128)),
					new ResultType(ImmutableList.of(VecType.V128, VecType.V128, VecType.V128))))))),
			ImmutableList.of(new Func(new TypeIdx(0), locals, new Expr(returnValues)),
				new Func(new TypeIdx(0), locals, new Expr(largeBody.build()))),
			ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), ImmutableList.of(),
			ImmutableList.of(), ImmutableList.of(), null, ImmutableList.of(),
			ImmutableList.of(new Export("normal", new ExportDesc.Func(new FuncIdx(0))),
				new Export("fallback", new ExportDesc.Func(new FuncIdx(1)))),
			new Names(null, ImmutableMap.of(),
				ImmutableMap.of(1, ImmutableMap.of(0, "input-vector", 2, "zero..vector", 3, "zero..vector")),
				ImmutableMap.of(), ImmutableMap.of(), ImmutableMap.of())
		);
		ModuleValidator.validateModule(module);
		var compiler = new ModuleCompiler(new CompilerOptions(ClassHierarchyResolver.defaultResolver(), "test.generated"));
		var realization = compiler.enqueueModule(module, "VectorLocals", name -> { throw new AssertionError(name); });
		var classes = new HashMap<String, byte[]>();
		for(var generator = compiler.dequeueGenerator(); generator != null; generator = compiler.dequeueGenerator()) {
			if(generator instanceof WasmClassGenerator classGenerator) {
				var bytes = classGenerator.generate();
				assertTrue(compiler.classFile().verify(bytes).isEmpty());
				var model = ClassFile.of().parse(bytes);
				classes.put(model.thisClass().asInternalName().replace('/', '.'), bytes);
				if(model.thisClass().asInternalName().endsWith("_SplitFunc_1")) {
					assertEquals(java.util.List.of("local0_input_vector", "local1", "local2_zero_vector", "local3_zero_vector"),
						model.fields().stream().map(f -> f.fieldName().stringValue()).filter(n -> n.startsWith("local")).toList());
					assertTrue(model.methods().stream().filter(m -> m.methodName().stringValue().startsWith("chunk")).count() >= 2);
				}
			}
		}
		assertFalse(classes.containsKey("test.generated.VectorLocals_SplitFunc_0"));
		assertTrue(classes.containsKey("test.generated.VectorLocals_SplitFunc_1"));
		var loader = new ClassLoader(getClass().getClassLoader()) {
			@Override protected Class<?> findClass(String name) throws ClassNotFoundException {
				var bytes = classes.get(name);
				if(bytes == null) throw new ClassNotFoundException(name);
				return defineClass(name, bytes, 0, bytes.length);
			}
		};
		var generated = loader.loadClass("test.generated.VectorLocals");
		var constructor = generated.getConstructors()[0];
		var instance = constructor.newInstance(new Object[constructor.getParameterCount()]);
		var parameter = new V128(123, 456);
		for(var export : realization.exports()) {
			var function = (WasmExportRealization.OfInstanceMethod)export;
			var method = generated.getMethod(function.methodName(), V128.class);
			var result = method.invoke(instance, parameter);
			var completed = Objects.requireNonNull(method.getReturnType().getMethod("get", method.getReturnType()).invoke(null, result));
			assertEquals(parameter, completed.getClass().getField("item0").get(completed), export.exportName());
			assertEquals(V128.ZERO, completed.getClass().getField("item1").get(completed), export.exportName());
			assertEquals(V128.ZERO, completed.getClass().getField("item2").get(completed), export.exportName());
		}
	}
}
