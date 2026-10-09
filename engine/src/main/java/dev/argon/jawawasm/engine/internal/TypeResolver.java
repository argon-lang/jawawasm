package dev.argon.jawawasm.engine.internal;

import com.google.common.collect.ImmutableList;
import dev.argon.jawawasm.format.modules.TypeIdx;
import dev.argon.jawawasm.format.types.*;

import org.jspecify.annotations.Nullable;

import java.util.function.UnaryOperator;

/**
 * Internal use only.
 * 
 * @hidden
 */
// Identity comparisons detect unchanged immutable children without structural
// traversal.
@SuppressWarnings("ReferenceEquality")
public sealed abstract class TypeResolver permits TypeClosure, TypeRoll, TypeUnroll {
	TypeResolver() {}

	public abstract HeapType resolveTypeIdx(TypeIdx idx);

	public abstract HeapType resolveRecTypeIdx(RecTypeIdx idx);

	public ValType resolveValType(ValType t) {
		return switch(t) {
			case BotType(),NumType _,VecType _ -> t;

			case RefType refType -> resolveRefType(refType);
		};
	}

	public RefType resolveRefType(RefType t) {
		var heapType = resolveHeapType(t.heapType());
		return heapType == t.heapType() ? t : new RefType(t.isNullable(), heapType);
	}

	public HeapType resolveHeapType(HeapType t) {
		return switch(t) {
			case HeapType.AbstractHeapType _,BotType() -> t;
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
		var args = resolveResultType(t.args());
		var results = resolveResultType(t.results());
		return args == t.args() && results == t.results() ? t : new FuncType(args, results);
	}

	public AggregateType resolveAggregateType(AggregateType t) {
		return switch(t) {
			case StructType structType -> {
				var fields = resolveList(structType.fields(), this::resolveFieldType);
				yield fields == structType.fields() ? structType : new StructType(fields);
			}
			case ArrayType arrayType -> {
				var field = resolveFieldType(arrayType.fieldType());
				yield field == arrayType.fieldType() ? arrayType : new ArrayType(field);
			}
		};
	}

	public DefType resolveDefType(DefType t) {
		var recursiveType = resolveRecursiveType(t.recursiveType());
		return recursiveType == t.recursiveType() ? t : new DefType(recursiveType, t.index());
	}

	public RecursiveType resolveRecursiveType(RecursiveType t) {
		var subtypes = resolveList(t.subtypes(), this::resolveSubType);
		return subtypes == t.subtypes() ? t : new RecursiveType(subtypes);
	}

	public SubType resolveSubType(SubType subType) {
		var superTypes = resolveList(subType.superTypes(), this::resolveHeapType);
		var compositeType = resolveCompositeType(subType.compositeType());
		return superTypes == subType.superTypes() && compositeType == subType.compositeType()
			? subType
			: new SubType(subType.isFinal(), superTypes, compositeType);
	}

	public FieldType resolveFieldType(FieldType t) {
		var storageType = resolveStorageType(t.storageType());
		return storageType == t.storageType() ? t : new FieldType(storageType, t.mut());
	}

	public ResultType resolveResultType(ResultType t) {
		var types = resolveList(t.types(), this::resolveValType);
		return types == t.types() ? t : new ResultType(types);
	}

	@SuppressWarnings("unchecked") // Immutable lists safely widen their element type.
	private static <T> ImmutableList<T> resolveList(ImmutableList<? extends T> items, UnaryOperator<T> resolve) {
		ImmutableList.@Nullable Builder<T> builder = null;
		for(int i = 0; i < items.size(); ++i) {
			T original = items.get(i);
			T resolved = resolve.apply(original);
			if(builder == null && resolved != original) {
				builder = ImmutableList.builderWithExpectedSize(items.size());
				for(int j = 0; j < i; ++j)
					builder.add(items.get(j));
			}
			if(builder != null)
				builder.add(resolved);
		}
		return builder == null ? (ImmutableList<T>)items : builder.build();
	}

	public TableType resolveTableType(TableType t) {
		var elementType = resolveRefType(t.elementType());
		return elementType == t.elementType() ? t : new TableType(t.addrType(), t.limits(), elementType);
	}

	public GlobalType resolveGlobalType(GlobalType t) {
		var type = resolveValType(t.type());
		return type == t.type() ? t : new GlobalType(t.mutability(), type);
	}
}
