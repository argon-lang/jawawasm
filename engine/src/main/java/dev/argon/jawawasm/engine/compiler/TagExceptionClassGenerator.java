package dev.argon.jawawasm.engine.compiler;

import dev.argon.jawawasm.format.types.FuncType;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.argon.jawawasm.engine.compiler.Constants.RUNTIME_PACKAGE;
import static dev.argon.jawawasm.engine.compiler.WasmClassGeneratorUtils.typeKind;
import static java.lang.constant.ConstantDescs.CD_void;

class TagExceptionClassGenerator extends WasmClassGenerator {

	public TagExceptionClassGenerator(ModuleCompiler compiler, ClassDesc outerClass, String className, FuncType tagFuncType) {
		super(compiler);
		this.outerClass = outerClass;
		this.className = outerClass.nested(className);
		simpleClassName = className;
		this.tagFuncType = tagFuncType;
	}

	private final ClassDesc outerClass;
	private final ClassDesc className;
	private final String simpleClassName;
	private final FuncType tagFuncType;

	@Override
	public ClassDesc className() {
		return className;
	}

	public TagRealization realization() {
		return new TagRealization(
			className,
			innerClassInfo(),
			constructorType()
		);
	}

	private InnerClassInfo innerClassInfo() {
		return InnerClassInfo.of(
			className,
			Optional.of(outerClass),
			Optional.of(simpleClassName),
			AccessFlag.PUBLIC,
			AccessFlag.STATIC,
			AccessFlag.FINAL
		);
	}

	private MethodTypeDesc constructorType() {
		List<ClassDesc> ctorParams = new ArrayList<>();
		for(var t : tagFuncType.args().types()) {
			var fieldType = compiler.getValType(t).type();
			ctorParams.add(fieldType);
		}

		return MethodTypeDesc.of(CD_void, ctorParams);
	}

	@Override
	public ClassHierarchyResolver.ClassHierarchyInfo hierarchyInfo() {
		return ClassHierarchyResolver.ClassHierarchyInfo.ofClass(
			ClassDesc.of(RUNTIME_PACKAGE, "WebAssemblyException")
		);
	}

	@Override
	byte[] generateImpl() {
		return compiler.classFile().build(
			className,
			clb -> {
				clb.withFlags(AccessFlag.PUBLIC, AccessFlag.FINAL);
				clb.withSuperclass(
					ClassDesc.of(RUNTIME_PACKAGE, "WebAssemblyException")
				);
				clb.with(InnerClassesAttribute.of(innerClassInfo()));

				for(int i = 0; i < tagFuncType.args().types().size(); ++i) {
					var t = tagFuncType.args().types().get(i);

					var name = "item" + i;
					var fieldType = compiler.getValType(t).type();
					clb.withField(name, fieldType, ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL);
				}

				clb.withMethodBody(
					"<init>",
					constructorType(),
					ClassFile.ACC_PUBLIC,
					cb -> {
						int slot = 1;
						for(int i = 0; i < tagFuncType.args().types().size(); ++i) {
							var t = tagFuncType.args().types().get(i);

							var name = "item" + i;
							var fieldType = compiler.getValType(t).type();
							var tk = typeKind(fieldType);

							cb.aload(0);
							cb.loadLocal(tk, slot);
							cb.putfield(className, name, fieldType);

							slot += tk.slotSize();
						}

						cb.aload(0);
						cb.invokespecial(
							ClassDesc.of(RUNTIME_PACKAGE, "WebAssemblyException"),
							"<init>",
							MethodTypeDesc.of(CD_void)
						);
						cb.return_();
					}
				);
			}
		);
	}
}
