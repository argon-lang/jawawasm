package dev.argon.jawawasm.engine.compiler;

import dev.argon.jawawasm.runtime.ModuleResolutionException;

class UncheckedModuleResolutionException extends RuntimeException {
	public UncheckedModuleResolutionException(ModuleResolutionException cause) {
		super(cause);
	}

	@SuppressWarnings("NullAway")
	@Override
	public ModuleResolutionException getCause() {
		return (ModuleResolutionException)super.getCause();
	}
}
