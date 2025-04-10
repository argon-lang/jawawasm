package dev.argon.jawawasm.engine.internal;

import com.google.common.collect.ImmutableList;
import dev.argon.jawawasm.format.modules.TypeIdx;
import dev.argon.jawawasm.format.types.*;

import java.util.ArrayList;

/**
 * Internal use only.
 * @hidden
 */
public sealed abstract class TypeResolver permits TypeClosure, TypeRoll, TypeUnroll {
	TypeResolver() {}

	public abstract HeapType resolveTypeIdx(TypeIdx idx);
	public abstract HeapType resolveRecTypeIdx(RecTypeIdx idx);

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
			case HeapType.AbstractHeapType _, BotType() -> t;
			case TypeIdx idx -> resolveTypeIdx(idx);
			case RecTypeIdx idx -> resolveRecTypeIdx(idx);
			case DefType defType -> resolveDefType(defType);
		};
	}

	public CompositeType resolveCompositeType(CompositeType t) {
		return switch(t) {
			case FuncType t2 -> resolveFuncType(t2);
			case AggregateType t2 -> resolveAggregateType(t2);
		};
	}

	public StorageType resolveStorageType(StorageType t) {
		return switch(t) {
			case PackedType _ -> t;
			case ValType valType -> resolveValType(valType);
		};
	}

	public FuncType resolveFuncType(FuncType t) {
		return new FuncType(resolveResultType(t.args()), resolveResultType(t.results()));
	}

	public AggregateType resolveAggregateType(AggregateType t) {
		return switch(t) {
			case StructType structType ->
				new StructType(
					structType.fields()
						.stream()
						.map(this::resolveFieldType)
						.collect(ImmutableList.toImmutableList())
				);

			case ArrayType arrayType ->
				new ArrayType(resolveFieldType(arrayType.fieldType()));
		};
	}


	public DefType resolveDefType(DefType t) {
		return new DefType(resolveRecursiveType(t.recursiveType()), t.index());
	}

	public RecursiveType resolveRecursiveType(RecursiveType t) {
		var resolvedSubTypes = ImmutableList.<SubType>builderWithExpectedSize(t.subtypes().size());

		for(var subType : t.subtypes()) {
			resolvedSubTypes.add(resolveSubType(subType));
		}

		return new RecursiveType(resolvedSubTypes.build());
	}

	public SubType resolveSubType(SubType subType) {
		var resolvedSuperTypes = ImmutableList.<HeapType>builder();
		for(var superType : subType.superTypes()) {
			resolvedSuperTypes.add(resolveHeapType(superType));
		}

		var resolvedType = resolveCompositeType(subType.compositeType());
		return new SubType(subType.isFinal(), resolvedSuperTypes.build(), resolvedType);
	}

	public FieldType resolveFieldType(FieldType t) {
		return new FieldType(resolveStorageType(t.storageType()), t.mut());
	}

	public ResultType resolveResultType(ResultType t) {
		return new ResultType(
			t.types()
				.stream()
				.map(this::resolveValType)
				.collect(ImmutableList.toImmutableList())
		);
	}

	public TableType resolveTableType(TableType t) {
		return new TableType(t.addrType(), t.limits(), resolveRefType(t.elementType()));
	}

	public GlobalType resolveGlobalType(GlobalType t) {
		return new GlobalType(t.mutability(), resolveValType(t.type()));
	}
}
