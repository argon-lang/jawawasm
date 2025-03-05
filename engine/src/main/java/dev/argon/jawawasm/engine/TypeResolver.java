package dev.argon.jawawasm.engine;

import dev.argon.jawawasm.format.modules.Module;
import dev.argon.jawawasm.format.modules.TypeIdx;
import dev.argon.jawawasm.format.types.*;

class TypeResolver {
	public TypeResolver(Module module) {
		this.module = module;
	}

	private final Module module;

	public ValType resolveValType(ValType t) {
		if(t instanceof RefType refType) {
			return resolveRefType(refType);
		}
		else {
			return t;
		}
	}

	public RefType resolveRefType(RefType t) {
		return new RefType(t.isNullable(), resolveHeapType(t.heapType()));
	}

	public HeapType resolveHeapType(HeapType t) {
		if(t instanceof TypeIdx idx) {
			return resolveFuncType(module.types().get(idx.index()));
		}
		else {
			return t;
		}
	}

	public FuncType resolveFuncType(FuncType t) {
		return new FuncType(resolveResultType(t.args()), resolveResultType(t.results()));
	}

	public ResultType resolveResultType(ResultType t) {
		return new ResultType(t.types().stream().map(this::resolveValType).toList());
	}

	public TableType resolveTableType(TableType t) {
		return new TableType(t.addrType(), t.limits(), resolveRefType(t.elementType()));
	}

	public GlobalType resolveGlobalType(GlobalType t) {
		return new GlobalType(t.mutability(), resolveValType(t.type()));
	}
}
