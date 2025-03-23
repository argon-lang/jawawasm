package dev.argon.jawawasm.engine.compiler;

import dev.argon.jawawasm.format.types.ArrayType;
import dev.argon.jawawasm.format.types.FuncType;
import dev.argon.jawawasm.format.types.Mut;
import dev.argon.jawawasm.format.types.SubType;
import org.jspecify.annotations.Nullable;

import java.lang.classfile.*;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import static dev.argon.jawawasm.engine.compiler.Constants.RUNTIME_PACKAGE;
import static dev.argon.jawawasm.engine.compiler.WasmClassGeneratorUtils.typeKind;
import static java.lang.constant.ConstantDescs.*;

class ArrayClassGenerator extends DefTypeClassGenerator {
	public ArrayClassGenerator(ModuleCompiler compiler, SubType subtype, ArrayType arrayType, String className) {
		super(compiler);
		this.subtype = subtype;
		this.arrayType = arrayType;
		this.className = ClassDesc.of(compiler.getOptions().javaPackage(), className);
	}

	private final SubType subtype;
	private final ArrayType arrayType;
	private final ClassDesc className;

	@Override
	public ClassDesc className() {
		return className;
	}

	@Override
	public ArrayTypeRealization realization() {
		return new ArrayTypeRealization(
			className,
			!subtype.isFinal(),
			() -> compiler.getStorageType(arrayType.fieldType().storageType()).type()
		);
	}

	@Override
	public ClassHierarchyResolver.ClassHierarchyInfo hierarchyInfo() {
		if(!subtype.superTypes().isEmpty() || !subtype.isFinal()) {
			throw new RuntimeException("Not implemented");
		}

		return ClassHierarchyResolver.ClassHierarchyInfo.ofInterface();
	}

	@Override
	protected byte[] generateImpl() {
		if(!subtype.superTypes().isEmpty() || !subtype.isFinal()) {
			throw new RuntimeException("Not implemented");
		}

		var superInterface = ClassDesc.of(RUNTIME_PACKAGE, "WasmArray");

		return compiler.classFile()
			.build(className, clb -> generateFinalArray(clb, className, superInterface));
	}

	private void generateFinalArray(ClassBuilder clb, ClassDesc thisClass, @Nullable ClassDesc superInterface) {
		var elementTypeRealization = compiler.getStorageType(arrayType.fieldType().storageType()).type();

		clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT);
		clb.withInterfaceSymbols(superInterface);

		clb.withField("array", elementTypeRealization.arrayType(), ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);

		clb.withMethodBody(
			"<init>",
			MethodTypeDesc.of(CD_void, CD_int),
			ClassFile.ACC_PUBLIC,
			cb -> {
				cb.aload(0);
				cb.iload(1);
				if(elementTypeRealization.isPrimitive()) {
					cb.newarray(typeKind(elementTypeRealization));
				}
				else {
					cb.anewarray(elementTypeRealization);
				}
				cb.putfield(thisClass, "array", elementTypeRealization.arrayType());

				cb.aload(0);
				cb.invokespecial(CD_Object, "<init>", MethodTypeDesc.of(CD_void));
				cb.return_();
			}
		);


		clb.withMethodBody(
			"length",
			MethodTypeDesc.of(CD_int),
			ClassFile.ACC_PUBLIC,
			cb -> {
				cb.aload(0);
				cb.getfield(thisClass, "array", elementTypeRealization.arrayType());
				cb.arraylength();
				cb.ireturn();
			}
		);

		clb.withMethodBody(
			"get",
			MethodTypeDesc.of(elementTypeRealization, CD_int),
			ClassFile.ACC_PUBLIC,
			cb -> {
				cb.aload(0);
				cb.getfield(thisClass, "array", elementTypeRealization.arrayType());
				cb.iload(1);
				cb.aaload();
				cb.return_(typeKind(elementTypeRealization));
			}
		);

		if(arrayType.fieldType().mut() == Mut.Var) {
			clb.withMethodBody(
				"set",
				MethodTypeDesc.of(CD_void, CD_int, elementTypeRealization),
				ClassFile.ACC_PUBLIC,
				cb -> {
					cb.aload(0);
					cb.getfield(thisClass, "array", elementTypeRealization.arrayType());
					cb.iload(1);
					cb.loadLocal(typeKind(elementTypeRealization), 2);
					cb.aastore();
					cb.return_();
				}
			);
		}

	}
}
