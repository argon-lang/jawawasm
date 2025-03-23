package dev.argon.jawawasm.engine.compiler;

import dev.argon.jawawasm.format.types.FuncType;
import dev.argon.jawawasm.format.types.ResultType;
import dev.argon.jawawasm.format.types.SubType;

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import static dev.argon.jawawasm.engine.compiler.WasmClassGeneratorUtils.*;
import static java.lang.constant.ConstantDescs.*;
import java.util.ArrayList;
import java.util.Optional;

class ResultClassGenerator extends WasmClassGenerator {
	public ResultClassGenerator(ModuleCompiler compiler, ResultType resultType, String className) {
		super(compiler);
		this.resultType = resultType;
		this.className = ClassDesc.of(compiler.getOptions().javaPackage(), className);
	}

	private final ResultType resultType;
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
					for(var t : resultType.types()) {
						var realization = compiler.getValType(t);
						ctorArgs.add(realization.type());
					}

					clb.withMethodBody(
						"of",
						MethodTypeDesc.of(className, ctorArgs),
						ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
						cb -> {
							cb.new_(endResultClass.className());
							cb.dup();

							int slotIndex = 0;

							for(int i = 0; i < resultType.types().size(); ++i) {
								cb.loadLocal(typeKind(ctorArgs.get(i)), slotIndex);
								slotIndex += slotSize(ctorArgs.get(i));
							}

							cb.invokespecial(endResultClass.className(), "<init>", MethodTypeDesc.of(CD_void, ctorArgs));
							cb.areturn();
						}
					);

					clb.withMethodBody(
						"get",
						MethodTypeDesc.of(endResultClass.className(), className),
						ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
						cb -> {

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
						}
					);
				}
			);
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
