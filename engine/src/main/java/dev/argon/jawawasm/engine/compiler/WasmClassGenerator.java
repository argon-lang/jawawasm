package dev.argon.jawawasm.engine.compiler;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.util.stream.Collectors;

public abstract class WasmClassGenerator implements WasmClassGeneratorBase {
	WasmClassGenerator() {}

	public abstract ClassDesc className();
	public final byte[] generate() {
		byte[] bc = generateImpl();
		var errors = ClassFile.of().verify(bc);
		if(!errors.isEmpty()) {
			var classModel = ClassFile.of().parse(bc);
			System.err.println(classModel);
			for(var method : classModel.methods()) {
				System.err.println(method);
				method.code().ifPresent(code -> code.elementList().forEach(System.err::println));

			}
			throw new RuntimeException("Verification errors for " + className() + ":\n" + errors.stream().map(VerifyError::toString).collect(Collectors.joining(",")));
		}

		return bc;
	}

	protected abstract byte[] generateImpl();
}
