package dev.argon.jawawasm.engine.validator;

import dev.argon.jawawasm.format.modules.Table;
import dev.argon.jawawasm.format.modules.TypeIdx;
import dev.argon.jawawasm.format.types.*;

public abstract class SubtypingBase {

	protected abstract FuncType resolveTypeIdx(TypeIdx idx);

	public boolean isSubtypeNum(NumType a, NumType b) {
		return a.equals(b);
	}

	public boolean isSubtypeVec(VecType a, VecType b) {
		return a.equals(b);
	}

	public boolean isSubtypeHeap(HeapType a, HeapType b) {
		return a.equals(b) ||
			(a instanceof FuncType && b == HeapType.AbstractHeapType.FUNC) ||
			(a instanceof FuncType af && b instanceof FuncType bf && isSubtypeFunc(af, bf)) ||
			(a instanceof TypeIdx at && isSubtypeHeap(resolveTypeIdx(at), b)) ||
			(b instanceof TypeIdx bt && isSubtypeHeap(a, resolveTypeIdx(bt))) ||
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
