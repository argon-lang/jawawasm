package dev.argon.jawawasm.engine.bytecode;

import java.io.IOException;
import java.io.InputStream;
import java.lang.constant.ClassDesc;

/**
 * Represents a Java class.
 */
public interface JavaClass {
	/**
	 * {@return The descriptor of the class.}
	 */
	ClassDesc descriptor();

	/**
	 * Read the classfile.
	 * @return An input stream representing the class file.
	 * @throws IOException if an IO error occurs.
	 */
	InputStream read() throws IOException;
}
