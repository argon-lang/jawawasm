package dev.argon.jawawasm.engine.compiler;

import dev.argon.jawawasm.format.types.*;
import dev.argon.jawawasm.runtime.V128;

import java.lang.classfile.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import static dev.argon.jawawasm.engine.compiler.WasmClassGeneratorUtils.*;
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
			() -> compiler.getStorageType(arrayType.fieldType().storageType()).type()
		);
	}

	@Override
	public ClassHierarchyResolver.ClassHierarchyInfo hierarchyInfo() {
		if(!subtype.superTypes().isEmpty() || !subtype.isFinal()) {
			throw new RuntimeException("Not implemented");
		}

		return switch(arrayType.fieldType().mut()) {
			case Const -> ClassHierarchyResolver.ClassHierarchyInfo.ofClass(wasmArrayImmutable);
			case Var -> ClassHierarchyResolver.ClassHierarchyInfo.ofClass(wasmArrayMutable);
		};
	}

	@Override
	protected byte[] generateImpl() {
		if(!subtype.superTypes().isEmpty() || !subtype.isFinal()) {
			throw new RuntimeException("Not implemented");
		}

		return compiler.classFile()
			.build(className, clb -> generateFinalArray(clb, className));
	}

	private void generateFinalArray(ClassBuilder clb, ClassDesc thisClass) {
		var elementTypeRealization = compiler.getStorageType(arrayType.fieldType().storageType()).type();


		var superClass = switch(arrayType.fieldType().mut()) {
			case Const -> wasmArrayImmutable;
			case Var -> wasmArrayMutable;
		};

		clb.withFlags(ClassFile.ACC_PUBLIC);
		clb.withSuperclass(superClass);

		clb.withField("array", elementTypeRealization.arrayType(), ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);


		clb.withMethodBody(
			"<init>",
			MethodTypeDesc.of(CD_void, elementTypeRealization.arrayType()),
			ClassFile.ACC_PRIVATE,
			cb -> {
				cb.aload(0);
				cb.aload(1);
				cb.putfield(thisClass, "array", elementTypeRealization.arrayType());

				cb.aload(0);
				cb.invokespecial(superClass, "<init>", MethodTypeDesc.of(CD_void));
				cb.return_();
			}
		);



		clb.withMethodBody(
			"nCopies",
			MethodTypeDesc.of(thisClass, elementTypeRealization, CD_int),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
			cb -> {
				cb.new_(thisClass);
				cb.dup();

				cb.iload(typeKind(elementTypeRealization).slotSize());
				if(elementTypeRealization.isPrimitive()) {
					cb.newarray(typeKind(elementTypeRealization));
				}
				else {
					cb.anewarray(elementTypeRealization);
				}
				cb.dup();
				cb.loadLocal(typeKind(elementTypeRealization), 0);
				if(elementTypeRealization.isPrimitive()) {
					cb.invokestatic(ClassDesc.of("java.util.Arrays"), "fill", MethodTypeDesc.of(CD_void, elementTypeRealization.arrayType(), elementTypeRealization));
				}
				else {
					cb.invokestatic(ClassDesc.of("java.util.Arrays"), "fill", MethodTypeDesc.of(CD_void, CD_Object.arrayType(), CD_Object));
				}

				cb.invokespecial(thisClass, "<init>", MethodTypeDesc.of(CD_void, elementTypeRealization.arrayType()));
				cb.areturn();
			}
		);

		if(isDefaultableDataType(arrayType.fieldType().storageType())) {
			clb.withMethodBody(
				"ofDefault",
				MethodTypeDesc.of(thisClass, CD_int),
				ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
				cb -> {
					if(elementTypeRealization.equals(v128Type)) {
						cb.iload(0);
						loadV128(cb, V128.ZERO);
						cb.invokestatic(
							thisClass,
							"nCopies",
							MethodTypeDesc.of(thisClass, elementTypeRealization, CD_int)
						);
						cb.areturn();
					}
					else {
						cb.new_(thisClass);
						cb.dup();

						cb.iload(0);
						if(elementTypeRealization.isPrimitive()) {
							cb.newarray(typeKind(elementTypeRealization));
						}
						else {
							cb.anewarray(elementTypeRealization);
						}

						cb.invokespecial(thisClass, "<init>", MethodTypeDesc.of(CD_void, elementTypeRealization.arrayType()));
						cb.areturn();
					}
				}
			);
		}

		clb.withMethodBody(
			"empty",
			MethodTypeDesc.of(thisClass),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
			cb -> {
				cb.new_(thisClass);
				cb.dup();

				cb.iconst_0();
				if(elementTypeRealization.isPrimitive()) {
					cb.newarray(typeKind(elementTypeRealization));
				}
				else {
					cb.anewarray(elementTypeRealization);
				}

				cb.invokespecial(thisClass, "<init>", MethodTypeDesc.of(CD_void, elementTypeRealization.arrayType()));
				cb.areturn();
			}
		);

		generateNewDataConstructors(clb, thisClass, elementTypeRealization);

		// Copy from another array.
		clb.withMethodBody(
			"copyOfJavaArray",
			MethodTypeDesc.of(thisClass, CD_int, CD_int, elementTypeRealization.arrayType()),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
			cb -> {
				// Create the Array.
				cb.iload(1);
				if(elementTypeRealization.isPrimitive()) {
					cb.newarray(typeKind(elementTypeRealization));
				}
				else {
					cb.anewarray(elementTypeRealization);
				}
				cb.astore(3);

				// arraycopy from input array
				cb.aload(2);
				cb.iload(0);
				cb.aload(3);
				cb.iconst_0();
				cb.iload(1);
				cb.invokestatic(ClassDesc.of("java.lang.System"), "arraycopy", MethodTypeDesc.of(CD_void, CD_Object, CD_int, CD_Object, CD_int, CD_int));

				cb.new_(thisClass);
				cb.dup();
				cb.aload(3);
				cb.invokespecial(thisClass, "<init>", MethodTypeDesc.of(CD_void, elementTypeRealization.arrayType()));
				cb.areturn();
			}
		);

		// Calls T[] with 0 and length
		clb.withMethodBody(
			"copyOfJavaArray",
			MethodTypeDesc.of(thisClass, elementTypeRealization.arrayType()),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
			cb -> {
				cb.iconst_0();
				cb.aload(0);
				cb.arraylength();
				cb.aload(0);
				cb.invokestatic(thisClass, "copyOfJavaArray", MethodTypeDesc.of(thisClass, CD_int, CD_int, elementTypeRealization.arrayType()));
				cb.areturn();
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
				cb.arrayLoad(typeKind(elementTypeRealization));
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
					cb.arrayStore(typeKind(elementTypeRealization));
					cb.return_();
				}
			);

			clb.withMethodBody(
				"fill",
				MethodTypeDesc.of(CD_void, CD_int, elementTypeRealization, CD_int),
				ClassFile.ACC_PUBLIC,
				cb -> {
					cb.iload(1);
					cb.iload(2 + typeKind(elementTypeRealization).slotSize());
					cb.aload(0);
					cb.getfield(thisClass, "array", elementTypeRealization.arrayType());
					cb.arraylength();
					cb.invokestatic(ClassDesc.of("java.util.Objects"), "checkFromIndexSize", MethodTypeDesc.of(CD_int, CD_int, CD_int, CD_int));
					cb.pop();


					cb.aload(0);
					cb.getfield(thisClass, "array", elementTypeRealization.arrayType());
					cb.iload(1);
					cb.iload(1);
					cb.iload(2 + typeKind(elementTypeRealization).slotSize());
					cb.iadd();
					cb.loadLocal(typeKind(elementTypeRealization), 2);
					if(elementTypeRealization.isPrimitive()) {
						cb.invokestatic(ClassDesc.of("java.util.Arrays"), "fill", MethodTypeDesc.of(CD_void, elementTypeRealization.arrayType(), CD_int, CD_int, elementTypeRealization));
					}
					else {
						cb.invokestatic(ClassDesc.of("java.util.Arrays"), "fill", MethodTypeDesc.of(CD_void, CD_Object.arrayType(), CD_int, CD_int, CD_Object));
					}

					cb.return_();
				}
			);

			if(isInitDataType(elementTypeRealization)) {
				clb.withMethodBody(
					"copyFromData",
					MethodTypeDesc.of(CD_void, CD_int, CD_int, CD_int, CD_byte.arrayType()),
					ClassFile.ACC_PUBLIC,
					cb -> {
						cb.aload(0);
						cb.getfield(thisClass, "array", elementTypeRealization.arrayType());
						cb.iload(1);
						cb.aload(4);
						cb.iload(2);
						cb.iload(3);
						cb.invokestatic(
							wasmArray,
							"initArrayFromData",
							MethodTypeDesc.of(CD_void, elementTypeRealization.arrayType(), CD_int, CD_byte.arrayType(), CD_int, CD_int)
						);
						cb.return_();
					}
				);
			}
		}

		clb.withMethodBody(
			"unsafeGetArray",
			MethodTypeDesc.of(CD_Object),
			ClassFile.ACC_PROTECTED,
			cb -> {
				cb.aload(0);
				cb.getfield(thisClass, "array", elementTypeRealization.arrayType());
				cb.areturn();
			}
		);

	}

	private boolean isDefaultableDataType(StorageType t) {
		return !(t instanceof RefType(var isNullable, _)) || isNullable;
	}

	private boolean isInitDataType(ClassDesc elementType) {
		return elementType == CD_byte ||
			elementType == CD_short ||
			elementType == CD_int ||
			elementType == CD_float ||
			elementType == CD_long ||
			elementType == CD_double ||
			elementType.equals(v128Type);
	}

	private void generateNewDataConstructors(ClassBuilder clb, ClassDesc thisClass, ClassDesc elementTypeRealization) {
		if(!isInitDataType(elementTypeRealization)) {
			return;
		}

		clb.withMethodBody(
			"copyOfData",
			MethodTypeDesc.of(thisClass, CD_int, CD_int, CD_byte.arrayType()),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
			cb -> {
				// Create the Array.
				cb.iload(1);
				if(elementTypeRealization.isPrimitive()) {
					cb.newarray(typeKind(elementTypeRealization));
				}
				else {
					cb.anewarray(elementTypeRealization);
				}
				cb.astore(3);

				cb.aload(3);
				cb.iconst_0();
				cb.aload(2);
				cb.iload(0);
				cb.iload(1);
				cb.invokestatic(wasmArray, "initArrayFromData", MethodTypeDesc.of(CD_void, elementTypeRealization.arrayType(), CD_int, CD_byte.arrayType(), CD_int, CD_int));

				cb.new_(thisClass);
				cb.dup();
				cb.aload(3);
				cb.invokespecial(thisClass, "<init>", MethodTypeDesc.of(CD_void, elementTypeRealization.arrayType()));
				cb.areturn();
			}
		);

		clb.withMethodBody(
			"copyOfData",
			MethodTypeDesc.of(thisClass, CD_byte.arrayType()),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
			cb -> {
				cb.aload(0);
				cb.arraylength();
				cb.iconst_0();
				cb.aload(0);
				cb.invokestatic(thisClass, "copyOfData", MethodTypeDesc.of(thisClass, CD_int, CD_int, CD_byte.arrayType()));
				cb.areturn();
			}
		);
	}
}
