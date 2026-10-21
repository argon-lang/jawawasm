package dev.argon.jawawasm.engine.tests;

import dev.argon.jawawasm.engine.MappedResolver;
import dev.argon.jawawasm.runtime.ModuleResolutionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MappedResolverTest {
	@Test
	public void resolvesRegisteredModulesAndAllowsReplacement() {
		var resolver = new MappedResolver<Object>();
		var first = new Object();
		resolver.put("module", first);
		assertSame(first, resolver.resolve("module"));
		var replacement = new Object();
		resolver.put("module", replacement);
		assertSame(replacement, resolver.resolve("module"));
	}

	@Test
	public void rejectsUnknownModules() {
		var resolver = new MappedResolver<Object>();
		assertThrows(ModuleResolutionException.class, () -> resolver.resolve("missing"));
	}
}
