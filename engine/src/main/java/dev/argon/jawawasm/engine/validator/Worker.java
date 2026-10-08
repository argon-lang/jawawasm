package dev.argon.jawawasm.engine.validator;

/** Receives caller-owned validation jobs for execution by its worker. */
@FunctionalInterface
interface Worker {
	void add(CodeValidateJob job);
}
