package dev.argon.jawawasm.engine;

import dev.argon.jawawasm.format.modules.TypeIdx;
import dev.argon.jawawasm.format.types.*;

public abstract class TypeResolver {

	protected abstract HeapType resolveTypeIdx(TypeIdx idx);

	public ValType resolveValType(ValType t) {
		return switch(t) {
			case BotType(), NumType _, VecType _ -> t;

			case RefType refType -> resolveRefType(refType);
		};
	}

	public RefType resolveRefType(RefType t) {
		return new RefType(t.isNullable(), resolveHeapType(t.heapType()));
	}

	public HeapType resolveHeapType(HeapType t) {
		return switch(t) {
			case HeapType.AbstractHeapType _, BotType(), RecTypeIdx _ -> t;
			case TypeIdx idx -> resolveTypeIdx(idx);
			case CompositeType compositeType -> resolveCompositeType(compositeType);
		};
	}

	public CompositeType resolveCompositeType(CompositeType t) {
		return switch(t) {
			case FuncType t2 -> resolveFuncType(t2);
			case AggregateType t2 -> resolveAggregateType(t2);
		};
	}

	private StorageType resolveStorageType(StorageType t) {
		return switch(t) {
			case PackedType _ -> t;
			case ValType valType -> resolveValType(valType);
		};
	}

	public FuncType resolveFuncType(FuncType t) {
		return new FuncType(resolveResultType(t.args()), resolveResultType(t.results()));
	}

	private AggregateType resolveAggregateType(AggregateType t) {
		return switch(t) {
			case StructType structType ->
				new StructType(structType.fields().stream().map(this::resolveFieldType).toList());

			case ArrayType arrayType ->
				new ArrayType(resolveFieldType(arrayType.fieldType()));
		};
	}

	public FieldType resolveFieldType(FieldType t) {
		return new FieldType(resolveStorageType(t.storageType()), t.mut());
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
