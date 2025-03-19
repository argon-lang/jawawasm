package dev.argon.jawawasm.engine.compiler;

import dev.argon.jawawasm.format.types.FuncType;
import dev.argon.jawawasm.format.types.ResultType;
import dev.argon.jawawasm.format.types.SubType;

import java.lang.classfile.Annotation;
import java.lang.classfile.ClassFile;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import static java.lang.constant.ConstantDescs.*;
import java.util.ArrayList;

class ResultClassGenerator extends WasmClassGenerator {
	public ResultClassGenerator(ModuleCompiler compiler, ResultType resultType, String className) {
		this.compiler = compiler;
		this.resultType = resultType;
		this.className = ClassDesc.of(compiler.getOptions().javaPackage(), className);
	}

	private final ModuleCompiler compiler;
	private final ResultType resultType;
	private final ClassDesc className;

	@Override
	public ClassDesc className() {
		return className;
	}

	@Override
	protected byte[] generateImpl() {
		// TODO: Convert to a trampoline class

		return ClassFile.of()
			.build(
				className,
				clb -> {
					var ctorArgs = new ArrayList<ClassDesc>();

					{
						int i = 0;
						for(var t : resultType.types()) {
							var realization = compiler.getValType(t);
							ctorArgs.add(realization.type());

							clb.withField("item" + i, realization.type(), ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL);

							++i;
						}
					}

					clb.withMethodBody(
						"<init>",
						MethodTypeDesc.of(CD_void, ctorArgs),
						ClassFile.ACC_PUBLIC,
						cb -> {
							int slotIndex = 1;

							for(int i = 0; i < resultType.types().size(); ++i) {
								cb.aload(0);
								cb.loadLocal(typeKind(ctorArgs.get(i)), slotIndex);
								cb.putfield(className, "item" + i, ctorArgs.get(i));
							}

							cb.aload(0);
							cb.invokespecial(CD_Object, "<init>", MethodTypeDesc.ofDescriptor("()V"));
							cb.return_();
						}
					);

					clb.withMethodBody(
						"of",
						MethodTypeDesc.of(className, ctorArgs),
						ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
						cb -> {
							cb.new_(className);
							cb.dup();

							int slotIndex = 0;

							for(int i = 0; i < resultType.types().size(); ++i) {
								cb.loadLocal(typeKind(ctorArgs.get(i)), slotIndex);
							}

							cb.invokespecial(className, "<init>", MethodTypeDesc.of(CD_void, ctorArgs));
							cb.areturn();
						}
					);
				}
			);
	}
}
