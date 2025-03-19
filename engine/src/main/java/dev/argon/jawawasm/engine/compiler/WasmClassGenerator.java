package dev.argon.jawawasm.engine.compiler;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.stream.Collectors;

public abstract class WasmClassGenerator implements WasmClassGeneratorBase {
	WasmClassGenerator(ModuleCompiler compiler) {
		this.compiler = compiler;
	}

	protected final ModuleCompiler compiler;

	public abstract ClassDesc className();
	public final byte[] generate() {
		byte[] bc = generateImpl();



//		ClassModel classModel = ClassFile.of().parse(bc);
//		Path p = Path.of("classes/" + classModel.thisClass().asInternalName() + ".class");
//		try {
//			Files.createDirectories(p.getParent());
//			Files.write(p, bc);
//		} catch(IOException e) {
//			throw new RuntimeException(e);
//		}

//		System.err.println(classModel);
//		for (MethodModel method : classModel.methods()) {
//			System.err.println(method);
//			method.code().ifPresent(codeModel -> {
//				System.err.println("Instructions for " + method.methodName().stringValue() + ":");
//				codeModel.elementList().forEach(System.err::println);
//			});
//		}
//		System.err.println(Base64.getEncoder().encodeToString(bc));

		return bc;
	}

	protected abstract byte[] generateImpl();
}
