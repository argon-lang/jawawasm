package dev.argon.jawawasm.engine.bytecode;

import com.google.errorprone.annotations.MustBeClosed;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.constant.ClassDesc;
import java.util.stream.Stream;

interface LibraryLoader extends Closeable {
	@MustBeClosed
	Stream<JavaClass> allClasses();

	default @Nullable JavaClass getJavaClass(ClassDesc classDesc) throws IOException {
		try {
			try(var classes = allClasses()) {
				return classes
					.filter(c -> c.descriptor().equals(classDesc))
					.findAny()
					.orElse(null);
			}
		}
		catch(UncheckedIOException e) {
			throw e.getCause();
		}
	}
}
