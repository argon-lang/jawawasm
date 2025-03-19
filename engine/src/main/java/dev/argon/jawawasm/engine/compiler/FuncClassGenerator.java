package dev.argon.jawawasm.engine.compiler;

import dev.argon.jawawasm.format.types.FuncType;
import dev.argon.jawawasm.format.types.SubType;

import java.lang.classfile.Annotation;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;

class FuncClassGenerator extends WasmClassGenerator {
	public FuncClassGenerator(ModuleCompiler compiler, SubType subtype, FuncType funcType, String className) {
		super(compiler);
		this.subtype = subtype;
		this.funcType = funcType;
		this.className = ClassDesc.of(compiler.getOptions().javaPackage(), className);
	}

	private final SubType subtype;
	private final FuncType funcType;
	private final ClassDesc className;

	@Override
	public ClassDesc className() {
		return className;
	}

	@Override
	protected byte[] generateImpl() {
		if(!subtype.superTypes().isEmpty()) {
			throw new RuntimeException("Not implemented");
		}

		var methodType = compiler.getMethodType(funcType);


		return compiler.getOptions().classFile()
			.build(
				className,
				clb -> clb
					.with(RuntimeVisibleAnnotationsAttribute.of(
						Annotation.of(ClassDesc.of("java.lang.FunctionalInterface"))
					))
					.withMethod(
						"invoke",
						methodType,
						ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT,
						mb -> {}
					)
			);
	}
}
