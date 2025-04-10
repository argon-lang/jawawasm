package dev.argon.jawawasm.engine.compiler;

import dev.argon.jawawasm.format.types.DefType;
import dev.argon.jawawasm.format.types.FuncType;
import dev.argon.jawawasm.format.types.SubType;

import java.lang.classfile.Annotation;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.HashSet;
import java.util.Set;

import static dev.argon.jawawasm.engine.internal.Constants.RUNTIME_PACKAGE;
import static dev.argon.jawawasm.engine.compiler.WasmClassGeneratorUtils.typeKind;

class FuncClassGenerator extends DefTypeClassGenerator {
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
	public FuncTypeRealization realization() {
		return new FuncTypeRealization(
			className,
			"invoke",
			() -> compiler.getMethodType(funcType),
			() -> {
				if(subtype.superTypes().isEmpty()) {
					return null;
				}

				return (FuncTypeRealization)compiler.getDefType((DefType)subtype.superTypes().getFirst());
			}
		);
	}

	@Override
	public ClassHierarchyResolver.ClassHierarchyInfo hierarchyInfo() {
		return ClassHierarchyResolver.ClassHierarchyInfo.ofInterface();
	}

	@Override
	protected byte[] generateImpl() {
		var methodType = compiler.getMethodType(funcType);


		return compiler.classFile()
			.build(
				className,
				clb -> {
					clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT);

					if(subtype.superTypes().isEmpty()) {
						clb.withInterfaceSymbols(ClassDesc.of(RUNTIME_PACKAGE, "WasmFunction"));
					}
					else {
						var superType = subtype.superTypes().getFirst();
						var superFuncRealization = (FuncTypeRealization)compiler.getDefType((DefType)superType);

						clb.withInterfaceSymbols(superFuncRealization.classDesc());

						Set<MethodTypeDesc> seenDescs = new HashSet<>();
						seenDescs.add(methodType.descriptor());

						while(superFuncRealization != null) {
							var superMethodType = superFuncRealization.methodType().get();
							if(seenDescs.add(superMethodType.descriptor())) {
								clb.withMethodBody(
									"invoke",
									superMethodType.descriptor(),
									ClassFile.ACC_PUBLIC | ClassFile.ACC_SYNTHETIC | ClassFile.ACC_BRIDGE,
									cb -> {
										cb.aload(0);
										int slot = 1;
										for(var param : superMethodType.descriptor().parameterList()) {
											var tk = typeKind(param);
											cb.loadLocal(tk, slot);
											slot += tk.slotSize();
										}
										cb.invokeinterface(className, "invoke", superMethodType.descriptor());
										cb.areturn();
									}
								);
							}

							superFuncRealization = superFuncRealization.superType().get();
						}
					}

					clb.with(RuntimeVisibleAnnotationsAttribute.of(
						Annotation.of(ClassDesc.of("java.lang.FunctionalInterface"))
					));
					clb.withMethod(
						"invoke",
						methodType.descriptor(),
						ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT,
						mb -> {
							mb.with(SignatureAttribute.of(methodType.methodSignature()));
							mb.with(RuntimeVisibleTypeAnnotationsAttribute.of(methodType.methodTypeAnnotations()));
						}
					);
				}
			);
	}
}
