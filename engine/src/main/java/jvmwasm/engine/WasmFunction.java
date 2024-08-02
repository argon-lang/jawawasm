package jvmwasm.engine;

import jvmwasm.format.types.FuncType;

public non-sealed interface WasmFunction extends WasmExport {
	FuncType type();
	FunctionResult invoke(Object[] args) throws Throwable;

	default Object[] invokeNow(Object[] args) throws Throwable {
		return FunctionResult.resolve(invoke(args));
	}

	default void checkType(FuncType t) {
		if(!t.equals(type())) {
			throw new IndirectCallTypeMismatchException();
		}
	}

}
