package dev.argon.jawawasm.engine.compiler;

import dev.argon.jawawasm.runtime.V128;

import java.lang.classfile.Annotation;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.Collections;

import static dev.argon.jawawasm.engine.compiler.Constants.*;
import static java.lang.constant.ConstantDescs.CD_byte;
import static java.lang.constant.ConstantDescs.CD_void;


final class WasmClassGeneratorUtils {
	private WasmClassGeneratorUtils() {}

	public static final ClassDesc mathClass = ClassDesc.of("java.lang.Math");
	public static final ClassDesc utilClass = ClassDesc.of(RUNTIME_PACKAGE, "Util");
	public static final ClassDesc wasmEq = ClassDesc.of(RUNTIME_PACKAGE, "WasmEq");
	public static final ClassDesc wasmTable = ClassDesc.of(RUNTIME_PACKAGE, "WasmTable");
	public static final ClassDesc wasmMemory = ClassDesc.of(RUNTIME_PACKAGE, "WasmMemory");
	public static final ClassDesc v128Type = ClassDesc.of(RUNTIME_PACKAGE, "V128");
	public static final ClassDesc wasmArray = ClassDesc.of(RUNTIME_PACKAGE, "WasmArray");
	public static final ClassDesc wasmArrayMutable = ClassDesc.of(RUNTIME_PACKAGE, "WasmArrayMutable");
	public static final ClassDesc wasmArrayImmutable = ClassDesc.of(RUNTIME_PACKAGE, "WasmArrayImmutable");
	public static final ClassDesc wasmStruct = ClassDesc.of(RUNTIME_PACKAGE, "WasmStruct");
	public static final ClassDesc i31Type = ClassDesc.of(RUNTIME_PACKAGE, "I31");

	public static final Annotation nullableAnn = Annotation.of(
		ClassDesc.of("org.jspecify.annotations.Nullable")
	);


	public static TypeKind typeKind(ClassDesc t) {
		return TypeKind.fromDescriptor(t.descriptorString());
	}

	public static int slotSize(ClassDesc t) {
		return typeKind(t).slotSize();
	}

	public static void loadV128(CodeBuilder cb, V128 value) {
		if(value.equals(V128.ZERO)) {
			cb.getstatic(v128Type, "ZERO", v128Type);
		}
		else {
			cb.new_(v128Type);
			cb.dup();
			for(int i = 0; i < 16; ++i) {
				cb.loadConstant(value.extractLane8(i));
			}
			cb.invokespecial(v128Type, "<init>", MethodTypeDesc.of(CD_void, Collections.nCopies(16, CD_byte)));
		}
	}
}
