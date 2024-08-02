package jvmwasm.engine.validator;

public abstract class ValidatorBase {
	ValidatorBase(Context context) {
		this.context = context;
	}

	protected final Context context;

	protected void require(boolean value, String message) throws ValidationException {
		if(!value) {
			throw new ValidationException(message);
		}
	}
}
