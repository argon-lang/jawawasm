package dev.argon.jawawasm.engine.compiler;

import dev.argon.jawawasm.format.modules.TypeIdx;
import dev.argon.jawawasm.format.types.*;

import java.lang.constant.ClassDesc;

import static dev.argon.jawawasm.engine.compiler.Constants.RUNTIME_PACKAGE;
import static java.lang.constant.ConstantDescs.*;

class TypeLoader {
	public TypeLoader(ModuleCompiler compiler) {
		this.compiler = compiler;
	}


	private final ModuleCompiler compiler;

	public TypeRealization getValType(ValType type) {
		return switch(type) {
			case BotType _ -> throw new RuntimeException("Unexpected bot type");
			case NumType numType -> new TypeRealization(
				switch(numType) {
					case I32 -> CD_int;
					case I64 -> CD_long;
					case F32 -> CD_float;
					case F64 -> CD_double;
				},
				false
			);
			case RefType refType -> new TypeRealization(
				getHeapType(refType.heapType()),
				refType.isNullable()
			);
			case VecType vecType -> new TypeRealization(
				switch(vecType) {
					case V128 -> ClassDesc.of("dev.argon.jawawasm.runtime.V128");
				},
				false
			);
		};
	}

	public ClassDesc getHeapType(HeapType type) {
		return switch(type) {
			case HeapType.AbstractHeapType abs -> switch(abs) {
				case EXN, NOEXN -> ClassDesc.of(RUNTIME_PACKAGE, "WebAssemblyException");
				case FUNC, NOFUNC -> ClassDesc.of(RUNTIME_PACKAGE, "WasmFunction");
				case EXTERN, NOEXTERN, ANY, NONE -> CD_Object;
				case EQ -> ClassDesc.of(RUNTIME_PACKAGE, "WasmEq");
				case I31 -> ClassDesc.of(RUNTIME_PACKAGE, "I32");
				case STRUCT -> ClassDesc.of(RUNTIME_PACKAGE, "WasmStruct");
				case ARRAY -> ClassDesc.of(RUNTIME_PACKAGE, "WasmArray");
			};

			case TypeIdx typeIdx -> throw new RuntimeException("Not implemented");
			case BotType botType -> throw new RuntimeException("Unexpected bot type");
			case DefType defType -> throw new RuntimeException("Not implemented: " + defType);

			case RecTypeIdx recTypeIdx -> throw new RuntimeException("Not implemented");
		};
	}

}
