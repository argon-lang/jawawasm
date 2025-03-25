package dev.argon.jawawasm.engine.compiler;

import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;

import static dev.argon.jawawasm.engine.compiler.Constants.*;


final class WasmClassGeneratorUtils {
	private WasmClassGeneratorUtils() {}

	public static final ClassDesc mathClass = ClassDesc.of("java.lang.Math");
	public static final ClassDesc utilClass = ClassDesc.of(RUNTIME_PACKAGE, "Util");
	public static final ClassDesc wasmTable = ClassDesc.of(RUNTIME_PACKAGE, "WasmTable");
	public static final ClassDesc wasmMemory = ClassDesc.of(RUNTIME_PACKAGE, "WasmMemory");
	public static final ClassDesc v128Type = ClassDesc.of(RUNTIME_PACKAGE, "V128");
	public static final ClassDesc wasmArray = ClassDesc.of(RUNTIME_PACKAGE, "WasmArray");
	public static final ClassDesc wasmArrayMutable = ClassDesc.of(RUNTIME_PACKAGE, "WasmArrayMutable");
	public static final ClassDesc wasmArrayImmutable = ClassDesc.of(RUNTIME_PACKAGE, "WasmArrayImmutable");
	public static final ClassDesc i31Type = ClassDesc.of(RUNTIME_PACKAGE, "I31");


	public static TypeKind typeKind(ClassDesc t) {
		return TypeKind.fromDescriptor(t.descriptorString());
	}

	public static int slotSize(ClassDesc t) {
		return typeKind(t).slotSize();
	}

}
