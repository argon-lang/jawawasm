package dev.argon.jawawasm.engine.validator;

import dev.argon.jawawasm.format.instructions.ControlInstr;
import dev.argon.jawawasm.format.modules.TypeIdx;
import dev.argon.jawawasm.format.types.*;

final class TypeValidator extends ValidatorBase {

	public TypeValidator(Context context) {
		super(context);
	}

	public void validateLimits(Limits limits, long maxSize) throws ValidationException {
		require(Long.compareUnsigned(limits.min(), maxSize) <= 0, "memory size must be at most 65536 pages (4GiB)");

		if(limits.max() != null) {
			require(Long.compareUnsigned(limits.max(), maxSize) <= 0, "memory size must be at most 65536 pages (4GiB)");
			require(Long.compareUnsigned(limits.min(), limits.max()) <= 0, "size minimum must not be greater than maximum");
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
		long maxSize = switch(tableType.addrType()) {
			case I32 -> Integer.toUnsignedLong(-1);
			case I64 -> -1L;
		};

		validateLimits(tableType.limits(), maxSize);
		validateReferenceType(tableType.elementType());
	}

	public void validateMemoryType(MemType memType) throws ValidationException {
		long maxSize = switch(memType.addrType()) {
			case I32 -> 1 << 16;
			case I64 -> 1L << 48;
		};

		validateLimits(memType.limits(), maxSize);
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
			case HeapType.AbstractHeapType _, BotType() -> {}
			case TypeIdx index -> context.requireType(index);
			case FuncType funcType -> validateFuncType(funcType);
		}
	}

	public void validateTagType(TagType t) throws ValidationException {
		context.requireType(t.funcType());
		var funcType = context.getType(t.funcType());
		require(funcType.results().types().isEmpty(), "Tag result type must be empty");
	}
}
