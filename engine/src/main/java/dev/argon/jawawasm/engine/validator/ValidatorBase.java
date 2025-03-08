package dev.argon.jawawasm.engine.validator;

/**
 * Base type for WebAssembly validators.
 */
public abstract class ValidatorBase {
	ValidatorBase(Context context) {
		this.context = context;
	}

	final Context context;

	void require(boolean value, String message) throws ValidationException {
		if(!value) {
			throw new ValidationException(message);
		}
	}
}
