package dev.argon.jawawasm.engine.validator;

import dev.argon.jawawasm.format.instructions.ControlInstr;
import dev.argon.jawawasm.format.modules.TypeIdx;
import dev.argon.jawawasm.format.types.*;

final class TypeValidator extends ValidatorBase {

	public TypeValidator(Context context) {
		super(context);
	}

	public void validateLimits(Limits limits, int maxSize) throws ValidationException {
		require(Integer.compareUnsigned(limits.min(), maxSize) <= 0, "memory size must be at most 65536 pages (4GiB)");

		if(limits.max() != null) {
			require(Integer.compareUnsigned(limits.max(), maxSize) <= 0, "memory size must be at most 65536 pages (4GiB)");
			require(Integer.compareUnsigned(limits.min(), limits.max()) <= 0, "size minimum must not be greater than maximum");
		}
	}

	public void validateBlockType(ControlInstr.BlockType blockType) throws ValidationException {
		switch(blockType) {
			case ControlInstr.BlockType.Empty() -> {}
			case ControlInstr.BlockType.OfIndex(var typeIdx) ->
				context.requireType(typeIdx);
			case ControlInstr.BlockType.OfValType(var t) -> {
				validateValType(t);
			}
		}
	}

	public void validateTableType(TableType tableType) throws ValidationException {
		validateLimits(tableType.limits(), -1);
		validateReferenceType(tableType.elementType());
	}

	public void validateMemoryType(MemType memType) throws ValidationException {
		validateLimits(memType.limits(), 1 << 16);
	}

	public void validateFuncType(FuncType funcType) throws ValidationException {
		validateResultType(funcType.args());
		validateResultType(funcType.results());
	}

	public void validateResultType(ResultType resultType) throws ValidationException {
		for(var t : resultType.types()) {
			validateValType(t);
		}
	}

	public void validateValType(ValType valType) throws ValidationException {
		switch(valType) {
			case NumType _, VecType _, BotType _ -> {}
			case RefType refType -> validateReferenceType(refType);
		}
	}

	public void validateReferenceType(RefType refType) throws ValidationException {
		validateHeapType(refType.heapType());
	}

	public void validateHeapType(HeapType heapType) throws ValidationException {
		switch(heapType) {
			case HeapType.Extern(), HeapType.Func(), BotType() -> {}
			case TypeIdx index -> context.requireType(index);
			case FuncType funcType -> validateFuncType(funcType);
		}
	}

}
