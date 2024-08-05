package dev.argon.jvmwasm.engine.validator;

import dev.argon.jvmwasm.format.instructions.ControlInstr;
import dev.argon.jvmwasm.format.types.Limits;
import dev.argon.jvmwasm.format.types.MemType;
import dev.argon.jvmwasm.format.types.TableType;

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
			case ControlInstr.BlockType.OfValType(var t) -> {}
		}
	}

	public void validateTableType(TableType tableType) throws ValidationException {
		validateLimits(tableType.limits(), -1);
	}

	public void validateMemoryType(MemType memType) throws ValidationException {
		validateLimits(memType.limits(), 1 << 16);
	}


}
