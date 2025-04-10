package dev.argon.jawawasm.engine.compiler;

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import static dev.argon.jawawasm.engine.internal.Constants.RUNTIME_PACKAGE;
import static dev.argon.jawawasm.engine.compiler.WasmClassGeneratorUtils.*;
import static java.lang.constant.ConstantDescs.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class ResultClassGenerator extends WasmClassGenerator {
	public ResultClassGenerator(ModuleCompiler compiler, ErasedResultType resultType, String className) {
		super(compiler);
		this.resultType = resultType;
		this.className = ClassDesc.of(compiler.getOptions().javaPackage(), className);
	}

	private final ErasedResultType resultType;
	private final ClassDesc className;

	@Override
	public ClassDesc className() {
		return className;
	}

	@Override
	public ClassHierarchyResolver.ClassHierarchyInfo hierarchyInfo() {
		return ClassHierarchyResolver.ClassHierarchyInfo.ofInterface();
	}

	@Override
	protected byte[] generateImpl() {
		var stepInterface = new StepClassGenerator();
		var endResultClass = new EndResultClassGenerator();

		compiler.enqueueGenerator(stepInterface);
		compiler.enqueueGenerator(endResultClass);

		return compiler.classFile()
			.build(
				className,
				clb -> {
					clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT);
					clb.withInterfaceSymbols(ClassDesc.of(RUNTIME_PACKAGE, "WasmResult"));
					clb.with(
						InnerClassesAttribute.of(
							InnerClassInfo.of(stepInterface.className(), Optional.of(className), Optional.of("Step"), ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_ABSTRACT),
							InnerClassInfo.of(endResultClass.className(), Optional.of(className), Optional.of("EndResult"), ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL)
						)
					);
					clb.with(
						PermittedSubclassesAttribute.ofSymbols(
							stepInterface.className(),
							endResultClass.className()
						)
					);
					clb.with(
						NestMembersAttribute.ofSymbols(
							stepInterface.className(),
							endResultClass.className()
						)
					);

					var ctorArgs = new ArrayList<ClassDesc>();
					var sigParams = new ArrayList<Signature.TypeParam>();
					var sigArgs = new ArrayList<Signature>();
					var sigTypeArgs = new ArrayList<Signature.TypeArg>();
					for(var t : resultType.types()) {
						ClassDesc desc = typeKindToErased(t);

						if(desc.equals(CD_Object)) {
							int paramIndex = sigParams.size();
							var paramName = "T" + paramIndex;
							sigParams.add(Signature.TypeParam.of(
								paramName,
								Optional.empty()
							));
							sigTypeArgs.add(Signature.TypeArg.of(Signature.TypeVarSig.of(paramName)));
							sigArgs.add(Signature.TypeVarSig.of(paramName));
						}
						else {
							sigArgs.add(Signature.BaseTypeSig.of(desc));
						}

						ctorArgs.add(desc);
					}

					var resultSig = Signature.ClassTypeSig.of(className, sigTypeArgs.toArray(Signature.TypeArg[]::new));
					var endResultSig = Signature.ClassTypeSig.of(resultSig, endResultClass.className);

					clb.with(SignatureAttribute.of(
						ClassSignature.of(
							sigParams,
							Signature.ClassTypeSig.of(CD_Object),
							Signature.ClassTypeSig.of(ClassDesc.of(RUNTIME_PACKAGE, "WasmResult"))
						)
					));

					clb.withMethod(
						"of",
						MethodTypeDesc.of(className, ctorArgs),
						ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
						mb -> {
							mb.with(SignatureAttribute.of(
								MethodSignature.of(
									sigParams,
									List.of(),
									resultSig,
									sigArgs.toArray(Signature[]::new)
								)
							));

							mb.withCode(cb -> {
								cb.new_(endResultClass.className());
								cb.dup();

								int slotIndex = 0;

								for(int i = 0; i < resultType.types().size(); ++i) {
									cb.loadLocal(typeKind(ctorArgs.get(i)), slotIndex);
									slotIndex += slotSize(ctorArgs.get(i));
								}

								cb.invokespecial(endResultClass.className(), "<init>", MethodTypeDesc.of(CD_void, ctorArgs));
								cb.areturn();
							});
						}
					);

					clb.withMethod(
						"get",
						MethodTypeDesc.of(endResultClass.className(), className),
						ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
						mb -> {
							mb.with(SignatureAttribute.of(
								MethodSignature.of(
									sigParams,
									List.of(),
									endResultSig,
									resultSig
								)
							));

							mb.withCode(cb -> {
								var startLabel = cb.newLabel();
								var endResultLabel = cb.newLabel();

								cb.labelBinding(startLabel);
								cb.aload(0);
								cb.instanceOf(stepInterface.className());
								cb.ifeq(endResultLabel);
								cb.aload(0);
								cb.checkcast(stepInterface.className());
								cb.invokeinterface(stepInterface.className(), "step", MethodTypeDesc.of(className));
								cb.astore(0);
								cb.goto_(startLabel);
								cb.labelBinding(endResultLabel);
								cb.aload(0);
								cb.checkcast(endResultClass.className());
								cb.areturn();
							});
						}
					);
				}
			);
	}

	private static ClassDesc typeKindToErased(TypeKind t) {
		return switch(t) {
			case BOOLEAN -> CD_boolean;
			case BYTE -> CD_byte;
			case CHAR -> CD_char;
			case SHORT -> CD_short;
			case INT -> CD_int;
			case LONG -> CD_long;
			case FLOAT -> CD_float;
			case DOUBLE -> CD_double;
			case REFERENCE -> CD_Object;
			case VOID -> CD_void;
		};
	}

	private final class EndResultClassGenerator extends WasmClassGenerator {
		EndResultClassGenerator() {
			super(ResultClassGenerator.this.compiler);
		}

		private final ClassDesc className = ResultClassGenerator.this.className.nested("EndResult");

		@Override
		public ClassDesc className() {
			return className;
		}

		@Override
		public ClassHierarchyResolver.ClassHierarchyInfo hierarchyInfo() {
			return ClassHierarchyResolver.ClassHierarchyInfo.ofClass(CD_Object);
		}

		@Override
		protected byte[] generateImpl() {
			return compiler.classFile()
				.build(
					className,
					clb -> {
						clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL);

						clb.withInterfaceSymbols(ResultClassGenerator.this.className);
						clb.with(NestHostAttribute.of(ResultClassGenerator.this.className));
						clb.with(
							InnerClassesAttribute.of(
								InnerClassInfo.of(className(), Optional.of(ResultClassGenerator.this.className), Optional.of("EndResult"), ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL)
							)
						);

						var ctorArgs = new ArrayList<ClassDesc>();

						{
							int i = 0;
							int typeParamIndex = 0;
							for(var t : resultType.types()) {
								var desc = typeKindToErased(t);
								Signature sig;

								if(desc.isPrimitive()) {
									sig = Signature.of(desc);
								}
								else {
									sig = Signature.TypeVarSig.of("T" + typeParamIndex);
								}

								ctorArgs.add(desc);

								clb.withField(
									"item" + i,
									desc,
									fb -> {
										fb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL);
										fb.with(SignatureAttribute.of(sig));
									}
								);

								++i;
							}
						}

						clb.withMethodBody(
							"<init>",
							MethodTypeDesc.of(CD_void, ctorArgs),
							ClassFile.ACC_PRIVATE,
							cb -> {
								int slotIndex = 1;

								for(int i = 0; i < resultType.types().size(); ++i) {
									cb.aload(0);
									cb.loadLocal(typeKind(ctorArgs.get(i)), slotIndex);
									cb.putfield(className, "item" + i, ctorArgs.get(i));
									slotIndex += slotSize(ctorArgs.get(i));
								}

								cb.aload(0);
								cb.invokespecial(CD_Object, "<init>", MethodTypeDesc.ofDescriptor("()V"));
								cb.return_();
							}
						);
					}
				);
		}
	}

	private final class StepClassGenerator extends WasmClassGenerator {
		StepClassGenerator() {
			super(ResultClassGenerator.this.compiler);
		}

		private final ClassDesc className = ResultClassGenerator.this.className.nested("Step");

		@Override
		public ClassDesc className() {
			return className;
		}

		@Override
		public ClassHierarchyResolver.ClassHierarchyInfo hierarchyInfo() {
			return ClassHierarchyResolver.ClassHierarchyInfo.ofInterface();
		}

		@Override
		protected byte[] generateImpl() {
			return compiler.classFile()
				.build(
					className,
					clb -> {
						clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT);

						clb.withInterfaceSymbols(ResultClassGenerator.this.className);
						clb.with(NestHostAttribute.of(ResultClassGenerator.this.className));
						clb.with(
							InnerClassesAttribute.of(
								InnerClassInfo.of(className, Optional.of(ResultClassGenerator.this.className), Optional.of("Step"), ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_ABSTRACT)
							)
						);


						clb.withMethod(
							"step",
							MethodTypeDesc.of(ResultClassGenerator.this.className),
							ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT,
							mb -> {}
						);
					}
				);
		}
	}
}
