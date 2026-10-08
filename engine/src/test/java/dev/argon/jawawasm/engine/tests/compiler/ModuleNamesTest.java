package dev.argon.jawawasm.engine.tests.compiler;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dev.argon.jawawasm.engine.compiler.CompilerOptions;
import dev.argon.jawawasm.engine.compiler.ModuleCompiler;
import dev.argon.jawawasm.format.modules.Module;
import dev.argon.jawawasm.format.modules.Names;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassHierarchyResolver;

import static org.junit.jupiter.api.Assertions.*;

public class ModuleNamesTest {
	@Test
	public void usesModuleNameWhenClassNameIsOmitted() {
		var compiler = compiler();
		var realization = compiler.enqueueModule(module(names("Example..__module")), null, name -> {
			throw new AssertionError("Unexpected import");
		});
		assertEquals(java.lang.constant.ClassDesc.of("test.generated", "Example_module"), realization.classDesc());
	}

	@Test
	public void explicitClassNameTakesPrecedence() {
		for(var module : ImmutableList.of(module(names("Other")), module(names("")), module(null))) {
			var realization = compiler().enqueueModule(module, "Explicit", name -> {
				throw new AssertionError("Unexpected import");
			});
			assertEquals("Explicit", realization.classDesc().displayName());
		}
	}

	@Test
	public void rejectsMissingOrEmptyModuleNamesWithoutClassName() {
		for(var module : ImmutableList.of(module(null), module(names(null)), module(names("")))) {
			var compiler = compiler();
			var error = assertThrows(IllegalArgumentException.class, () -> compiler.enqueueModule(module, null, name -> {
				throw new AssertionError("Unexpected import");
			}));
			assertTrue(error.getMessage().contains("class name"));
			assertNull(compiler.dequeueGenerator());
		}
	}

	private static ModuleCompiler compiler() {
		return new ModuleCompiler(new CompilerOptions(ClassHierarchyResolver.defaultResolver(), "test.generated"));
	}

	private static Names names(@Nullable String moduleName) {
		return new Names(moduleName, ImmutableMap.of(), ImmutableMap.of(), ImmutableMap.of(), ImmutableMap.of(), ImmutableMap.of());
	}

	private static Module module(@Nullable Names names) {
		return new Module(ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), ImmutableList.of(),
			ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), null, ImmutableList.of(), ImmutableList.of(), names);
	}
}
