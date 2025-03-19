package dev.argon.jawawasm.engine.compiler;

import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;

import static dev.argon.jawawasm.engine.compiler.Constants.*;

interface WasmClassGeneratorBase {

	static final ClassDesc mathClass = ClassDesc.of("java.lang.Math");
	static final ClassDesc utilClass = ClassDesc.of(RUNTIME_PACKAGE, "Util");


	default TypeKind typeKind(ClassDesc t) {
		return TypeKind.fromDescriptor(t.descriptorString());
	}

	default int slotSize(ClassDesc t) {
		return typeKind(t).slotSize();
	}

}
