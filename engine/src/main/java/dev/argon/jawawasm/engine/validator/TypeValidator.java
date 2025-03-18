package dev.argon.jawawasm.engine.validator;

import dev.argon.jawawasm.engine.internal.TypeUnroll;
import dev.argon.jawawasm.format.instructions.ControlInstr;
import dev.argon.jawawasm.format.modules.TypeIdx;
import dev.argon.jawawasm.format.types.*;
import dev.argon.jawawasm.format.types.Limits;

final class TypeValidator extends ValidatorBase {

	public TypeValidator(Context context) {
		super(context);
	}

	public void validateLimits(Limits limits, long maxSize, String message) throws ValidationException {
		require(Long.compareUnsigned(limits.min(), maxSize) <= 0, message);

		if(limits.max() != null) {
			require(Long.compareUnsigned(limits.max(), maxSize) <= 0, message);
			require(Long.compareUnsigned(limits.min(), limits.max()) <= 0, "size minimum must not be greater than maximum");
		}
	}

	public void validateBlockType(ControlInstr.BlockType blockType) throws ValidationException {
		switch(blockType) {
			case ControlInstr.BlockType.Empty() -> {}
			case ControlInstr.BlockType.OfIndex(var typeIdx) ->
				context.requireFuncType(typeIdx);
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

		validateLimits(tableType.limits(), maxSize, "table size");
		validateReferenceType(tableType.elementType());
	}

	public void validateMemoryType(MemType memType) throws ValidationException {
		long maxSize = switch(memType.addrType()) {
			case I32 -> 1 << 16;
			case I64 -> 1L << 48;
		};

		validateLimits(memType.limits(), maxSize, "memory size");
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
			case HeapType.AbstractHeapType _, BotType(), RecTypeIdx _, DefType _ -> {}
			case TypeIdx index -> context.requireType(index);
		}
	}

	public void validateCompositeType(CompositeType compositeType) throws ValidationException {
		switch(compositeType) {
			case FuncType funcType -> validateFuncType(funcType);
			case StructType structType -> {
				for(var fieldType : structType.fields()) {
					validateFieldType(fieldType);
				}
			}
			case ArrayType(var fieldType) -> validateFieldType(fieldType);
		}
	}

	private void validateStorageType(StorageType storageType) throws ValidationException {
		switch(storageType) {
			case PackedType _ -> {}
			case ValType valType -> validateValType(valType);
		}
	}

	private void validateFieldType(FieldType fieldType) throws ValidationException {
		validateStorageType(fieldType.storageType());
	}

	public void validateTagType(TagType t) throws ValidationException {
		context.requireFuncType(t.funcType());
		var funcType = (FuncType)context.getCompositeType(t.funcType());
		require(funcType.results().types().isEmpty(), "non-empty tag result type");
	}

	public void validateRecursiveType(RecursiveType recType, int recTypeStart) throws ValidationException {
		int typeIndex = recTypeStart;
		for(var subType : recType.subtypes()) {
			validateSubType(subType, recTypeStart, typeIndex);

			++typeIndex;
		}
	}

	private void validateSubType(SubType subType, int recTypeStart, int typeIndex) throws ValidationException {
		validateCompositeType(subType.compositeType());

		if(subType.superTypes().size() > 1) {
			throw new ValidationException("More than one supertype");
		}

		for(var superTypeHeap : subType.superTypes()) {
			var superTypeIndex = (TypeIdx)superTypeHeap;

			require(superTypeIndex.index() < typeIndex, "supertype must have smaller index");
			context.requireType(superTypeIndex);
			var superType = TypeUnroll.unroll(context.getType(superTypeIndex));

			require(!superType.isFinal(), "sub type has final super type");

			require(new Subtyping(context).isSubtypeComposite(subType.compositeType(), superType.compositeType()), "sub type " + typeIndex + " does not match super type: this subtype: " + subType.compositeType() + ", super type: " + superType.compositeType());
		}
	}
}
