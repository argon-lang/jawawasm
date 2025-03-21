package dev.argon.jawawasm.engine.compiler;

import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;

import static dev.argon.jawawasm.engine.compiler.Constants.*;


final class WasmClassGeneratorUtils {
	private WasmClassGeneratorUtils() {}

	public static final ClassDesc mathClass = ClassDesc.of("java.lang.Math");
	public static final ClassDesc utilClass = ClassDesc.of(RUNTIME_PACKAGE, "Util");
	public static final ClassDesc wasmTable = ClassDesc.of(RUNTIME_PACKAGE, "WasmTable");;


	public static TypeKind typeKind(ClassDesc t) {
		return TypeKind.fromDescriptor(t.descriptorString());
	}

	public static int slotSize(ClassDesc t) {
		return typeKind(t).slotSize();
	}

}
