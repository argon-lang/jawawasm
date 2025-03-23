package dev.argon.jawawasm.engine.compiler;

abstract class DefTypeClassGenerator extends WasmClassGenerator {
	DefTypeClassGenerator(ModuleCompiler compiler) {
		super(compiler);
	}

	public abstract DefTypeRealization realization();
}
