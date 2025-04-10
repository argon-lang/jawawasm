package dev.argon.jawawasm.engine.internal;

import dev.argon.jawawasm.format.modules.TypeIdx;
import dev.argon.jawawasm.format.types.*;
import dev.argon.jawawasm.format.types.Limits;
import org.jspecify.annotations.Nullable;

/**
 * Internal use only.
 * @hidden
 */
public abstract class SubtypingBase {

	public abstract HeapType resolveTypeIdx(TypeIdx idx);
	
	
	public boolean isSubtypeNum(NumType a, NumType b) {
		return a.equals(b);
	}

	public boolean isSubtypeVec(VecType a, VecType b) {
		return a.equals(b);
	}

	public boolean isSubtypeHeap(HeapType a, HeapType b) {
		if(a.equals(b)) {
			return true;
		}

		if(a == HeapType.AbstractHeapType.EQ && b == HeapType.AbstractHeapType.ANY) {
			return true;
		}

		if(
			(
				a == HeapType.AbstractHeapType.I31 ||
					a == HeapType.AbstractHeapType.STRUCT ||
					a == HeapType.AbstractHeapType.ARRAY
			) && isSubtypeHeap(HeapType.AbstractHeapType.EQ, b)
		) {
			return true;
		}

		if(a instanceof TypeIdx at) {
			return isSubtypeHeap(resolveTypeIdx(at), b);
		}

		if(b instanceof TypeIdx bt) {
			return isSubtypeHeap(a, resolveTypeIdx(bt));
		}

		if(a == HeapType.AbstractHeapType.NONE) {
			return isSubtypeHeap(b, HeapType.AbstractHeapType.ANY);
		}

		if(a == HeapType.AbstractHeapType.NOFUNC) {
			return isSubtypeHeap(b, HeapType.AbstractHeapType.FUNC);
		}

		if(a == HeapType.AbstractHeapType.NOEXN) {
			return isSubtypeHeap(b, HeapType.AbstractHeapType.EXN);
		}

		if(a == HeapType.AbstractHeapType.NOEXTERN) {
			return isSubtypeHeap(b, HeapType.AbstractHeapType.EXTERN);
		}

		if(a instanceof DefType ad) {
			return isSubtypeDefTypeWith(ad, b);
		}

		if(a instanceof BotType) {
			return true;
		}

		return false;
	}

	private boolean isSubtypeDefTypeWith(DefType a, HeapType b) {
		var aSubType = TypeUnroll.unroll(a);
		var aExpand = aSubType.compositeType();
		return (aExpand instanceof StructType && isSubtypeHeap(HeapType.AbstractHeapType.STRUCT, b)) ||
			(aExpand instanceof ArrayType && isSubtypeHeap(HeapType.AbstractHeapType.ARRAY, b)) ||
			(aExpand instanceof FuncType && isSubtypeHeap(HeapType.AbstractHeapType.FUNC, b)) ||
			(b instanceof DefType bd && isSubtypeDefType(a, aSubType, bd));
	}

	public boolean isSubtypeDefType(DefType a, DefType b) {
		return isSubtypeDefType(a, null, b);
	}

	private boolean isSubtypeDefType(DefType a, @Nullable SubType aSubType, DefType b) {
		if(close(a).equals(close(b))) {
			return true;
		}

		if(aSubType == null) {
			aSubType = TypeUnroll.unroll(a);
		}

		for(var superType : aSubType.superTypes()) {
			if(isSubtypeHeap(superType, b)) {
				return true;
			}
		}

		return false;
	}

	public boolean isSubtypeRef(RefType a, RefType b) {
		return (!a.isNullable() || b.isNullable()) && isSubtypeHeap(a.heapType(), b.heapType());
	}

	public boolean isSubtypeVal(ValType a, ValType b) {
		return (a instanceof NumType an && b instanceof NumType bn && isSubtypeNum(an, bn)) ||
			(a instanceof VecType av && b instanceof VecType bv && isSubtypeVec(av, bv)) ||
			(a instanceof RefType ar && b instanceof RefType br && isSubtypeRef(ar, br)) ||
			a instanceof BotType;
	}

	public boolean isSubtypeStorage(StorageType a, StorageType b) {
		return switch(a) {
			case ValType av -> b instanceof ValType bv && isSubtypeVal(av, bv);
			case PackedType ap -> ap == b;
		};
	}

	public boolean isSubtypeResult(ResultType a, ResultType b) {
		if(a.types().size() != b.types().size()) {
			return false;
		}

		for(int i = 0; i < a.types().size(); ++i) {
			if(!isSubtypeVal(a.types().get(i), b.types().get(i))) {
				return false;
			}
		}

		return true;
	}

	public boolean isSubtypeComposite(CompositeType a, CompositeType b) {
		return switch(a) {
			case FuncType af -> b instanceof FuncType bf && isSubtypeFunc(af, bf);
			case StructType aStruct -> {
				if(!(b instanceof StructType bStruct)) {
					yield false;
				}

				if(aStruct.fields().size() < bStruct.fields().size()) {
					yield false;
				}

				for(int i = 0; i < bStruct.fields().size(); ++i) {
					if(!isSubtypeField(aStruct.fields().get(i), bStruct.fields().get(i))) {
						yield false;
					}
				}

				yield true;
			}

			case ArrayType aArray -> {
				if(!(b instanceof ArrayType bArray)) {
					yield false;
				}

				yield isSubtypeField(aArray.fieldType(), bArray.fieldType());
			}
		};
	}

	public boolean isSubtypeField(FieldType a, FieldType b) {
		if(!isSubtypeStorage(a.storageType(), b.storageType())) {
			return false;
		}

		return switch(a.mut()) {
			case Const -> b.mut() == Mut.Const;

			case Var -> {
				if(b.mut() != Mut.Var) {
					yield false;
				}

				yield isSubtypeStorage(b.storageType(), a.storageType());
			}
		};
	}

	public boolean isSubtypeFunc(FuncType a, FuncType b) {
		return isSubtypeResult(b.args(), a.args()) && isSubtypeResult(a.results(), b.results());
	}

	public boolean isSubtypeLimits(Limits a, Limits b) {
		return a.min() >= b.min() && (
			b.max() == null ||
				(a.max() != null && a.max() <= b.max())
		);
	}

	public boolean isSubtypeLimitsIgnoreMin(Limits a, Limits b) {
		return b.max() == null || (a.max() != null && a.max() <= b.max());
	}

	public boolean isSubtypeTable(TableType a, TableType b) {
		return a.addrType() == b.addrType() && isSubtypeLimits(a.limits(), b.limits()) &&
			isSubtypeRef(a.elementType(), b.elementType()) &&
			isSubtypeRef(b.elementType(), a.elementType());
	}

	public boolean isSubtypeTableIgnoreMin(TableType a, TableType b) {
		return a.addrType() == b.addrType() && isSubtypeLimitsIgnoreMin(a.limits(), b.limits()) &&
			isSubtypeRef(a.elementType(), b.elementType()) &&
			isSubtypeRef(b.elementType(), a.elementType());
	}

	public boolean isSubtypeMemory(MemType a, MemType b) {
		return a.addrType() == b.addrType() && isSubtypeLimits(a.limits(), b.limits());
	}

	public boolean isSubtypeMemoryIgnoreMin(MemType a, MemType b) {
		return a.addrType() == b.addrType() && isSubtypeLimitsIgnoreMin(a.limits(), b.limits());
	}

	public boolean isSubtypeGlobal(GlobalType a, GlobalType b) {
		return switch(a.mutability()) {
			case Const -> b.mutability() == Mut.Const && isSubtypeVal(a.type(), b.type());
			case Var -> b.mutability() == Mut.Var && isSubtypeVal(a.type(), b.type()) && isSubtypeVal(b.type(), a.type());
		};
	}

	private DefType close(DefType t) {
		var close = new TypeClosure() {
			@Override
			public HeapType resolveTypeIdx(TypeIdx idx) {
				return resolveHeapType(SubtypingBase.this.resolveTypeIdx(idx));
			}
		};

		return close.resolveDefType(t);
	}
}
