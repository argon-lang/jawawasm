package dev.argon.jawawasm.engine.compiler;

import dev.argon.jawawasm.format.types.DefType;

abstract class DefTypeClassGenerator extends WasmClassGenerator {
	DefTypeClassGenerator(ModuleCompiler compiler, DefType declaredType) {
		super(compiler);
		this.declaredType = declaredType;
	}

	final DefType declaredType;

	public abstract DefTypeRealization realization();
}
