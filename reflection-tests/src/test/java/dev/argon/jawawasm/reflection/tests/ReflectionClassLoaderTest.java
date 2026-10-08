package dev.argon.jawawasm.reflection.tests;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReflectionClassLoaderTest {
	@Test
	public void instantiateWithRuntimeOutsideSystemLoader() throws Exception {
		String trapName = "dev.argon.jawawasm.runtime.UnreachableTrap";
		assertThrows(ClassNotFoundException.class,
			() -> Class.forName(trapName, false, ClassLoader.getSystemClassLoader()));
		var entries = Pattern.compile(Pattern.quote(File.pathSeparator)).split(requireNonNull(System.getProperty("reflectionTestClasspath")), -1);
		var urls = new URL[entries.length];
		for(int i = 0; i < entries.length; ++i) {
			urls[i] = Path.of(entries[i]).toUri().toURL();
		}
		try(var loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
			Class.forName(trapName, false, loader);
			var success = loader.loadClass("dev.argon.jawawasm.reflection.driver.ReflectionClassLoaderDriver")
				.getMethod("run").invoke(null);
			assertTrue((Boolean) requireNonNull(success), "ReflectionEngine failed in isolated loader");
		}
	}
}
