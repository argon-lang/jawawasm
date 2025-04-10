package dev.argon.jawawasm.engine.compiler.tests;

import com.google.common.collect.ImmutableList;
import dev.argon.jawawasm.engine.compiler.CompilerOptions;
import dev.argon.jawawasm.engine.compiler.ErasedResultType;
import dev.argon.jawawasm.engine.compiler.ModuleCompiler;
import dev.argon.jawawasm.engine.reflection.ReflectionModuleLoader;
import dev.argon.jawawasm.format.ModuleFormatException;
import dev.argon.jawawasm.runtime.ResultVoid;
import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassHierarchyResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RealizationTests {

	private void testResultRealization(Class<?> resultClass, ErasedResultType expectedRealization) throws ModuleFormatException {
		{
			var compiler = new ModuleCompiler(new CompilerOptions(
				ClassHierarchyResolver.defaultResolver(),
				"dev.argon.jawawasm.engine.compiler.tests.test"
			));
			var loader = new ReflectionModuleLoader(compiler);

			assertEquals(expectedRealization, loader.loadResultClass(resultClass));
		}
	}

	@Test
	public void resultVoidRealization() throws ModuleFormatException {
		testResultRealization(ResultVoid.class, new ErasedResultType(ImmutableList.of()));
	}

}
