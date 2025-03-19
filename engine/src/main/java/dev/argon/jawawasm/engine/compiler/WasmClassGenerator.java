package dev.argon.jawawasm.engine.compiler;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.util.stream.Collectors;

public abstract class WasmClassGenerator implements WasmClassGeneratorBase {
	WasmClassGenerator(ModuleCompiler compiler) {
		this.compiler = compiler;
	}

	protected final ModuleCompiler compiler;

	public abstract ClassDesc className();
	public final byte[] generate() {
		return generateImpl();
	}

	protected abstract byte[] generateImpl();
}
