package dev.argon.jawawasm.engine;

import dev.argon.jawawasm.format.modules.TypeIdx;
import dev.argon.jawawasm.format.types.*;

public abstract class SubtypingBase extends TypeResolver {

	public boolean isSubtypeNum(NumType a, NumType b) {
		return a.equals(b);
	}

	public boolean isSubtypeVec(VecType a, VecType b) {
		return a.equals(b);
	}

	public boolean isSubtypeHeap(HeapType a, HeapType b) {
		return a.equals(b) ||
			(a == HeapType.AbstractHeapType.EQ && b == HeapType.AbstractHeapType.ANY) ||
			(
				(
					a == HeapType.AbstractHeapType.I32 ||
					a == HeapType.AbstractHeapType.STRUCT ||
					a == HeapType.AbstractHeapType.ARRAY
				) && b == HeapType.AbstractHeapType.EQ
			) ||
			(a instanceof StructType && b == HeapType.AbstractHeapType.STRUCT) ||
			(a instanceof ArrayType && b == HeapType.AbstractHeapType.ARRAY) ||
			(a instanceof FuncType && b == HeapType.AbstractHeapType.FUNC) ||
			(a instanceof CompositeType ad && b instanceof CompositeType bd && isSubtypeComposite(ad, bd)) ||
			(a instanceof TypeIdx at && isSubtypeHeap(resolveTypeIdx(at), b)) ||
			(b instanceof TypeIdx bt && isSubtypeHeap(a, resolveTypeIdx(bt))) ||
			(a == HeapType.AbstractHeapType.NONE && isSubtypeHeap(b, HeapType.AbstractHeapType.ANY)) ||
			(a == HeapType.AbstractHeapType.NOFUNC && isSubtypeHeap(b, HeapType.AbstractHeapType.FUNC)) ||
			(a == HeapType.AbstractHeapType.NOEXN && isSubtypeHeap(b, HeapType.AbstractHeapType.EXN)) ||
			(a == HeapType.AbstractHeapType.NOEXTERN && isSubtypeHeap(b, HeapType.AbstractHeapType.EXTERN)) ||
			a instanceof BotType;
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
			case Const -> b.mut() != Mut.Const;

			case Var -> {
				if(b.mut() != Mut.Var) {
					yield false;
				}

				yield isSubtypeStorage(b.storageType(), a.storageType());
			}
		};
	}

	public boolean isSubtypeFunc(FuncType a, FuncType b) {
		return isSubtypeResult(a.results(), b.results()) &&
			isSubtypeResult(b.results(), a.results()) &&
			isSubtypeResult(a.args(), b.args()) &&
			isSubtypeResult(b.args(), a.args());
	}

	public boolean isSubtypeLimits(Limits a, Limits b) {
		return a.min() >= b.min() && (
			b.max() == null ||
				(a.max() != null && a.max() <= b.max())
		);
	}

	public boolean isSubtypeTable(TableType a, TableType b) {
		return a.addrType() == b.addrType() && isSubtypeLimits(a.limits(), b.limits()) &&
			isSubtypeRef(a.elementType(), b.elementType()) &&
			isSubtypeRef(b.elementType(), a.elementType());
	}

	public boolean isSubtypeMemory(MemType a, MemType b) {
		return a.addrType() == b.addrType() && isSubtypeLimits(a.limits(), b.limits());
	}

	public boolean isSubtypeGlobal(GlobalType a, GlobalType b) {
		return switch(a.mutability()) {
			case Const -> b.mutability() == Mut.Const && isSubtypeVal(a.type(), b.type());
			case Var -> b.mutability() == Mut.Var && isSubtypeVal(a.type(), b.type()) && isSubtypeVal(b.type(), a.type());
		};
	}
}
