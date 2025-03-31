package dev.argon.jawawasm.engine.compiler;

import dev.argon.jawawasm.format.types.*;
import dev.argon.jawawasm.runtime.V128;

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
			() -> compiler.getStorageType(arrayType.fieldType().storageType()).type(),
			() -> {
				if(subtype.superTypes().isEmpty()) {
					return null;
				}

				return (ArrayTypeRealization)compiler.getDefType((DefType)subtype.superTypes().getFirst());
			}
		);
	}

	@Override
	public ClassHierarchyResolver.ClassHierarchyInfo hierarchyInfo() {
		if(subtype.superTypes().isEmpty()) {
			return switch(arrayType.fieldType().mut()) {
				case Const -> ClassHierarchyResolver.ClassHierarchyInfo.ofClass(wasmArrayImmutable);
				case Var -> ClassHierarchyResolver.ClassHierarchyInfo.ofClass(wasmArrayMutable);
			};
		}

		return ClassHierarchyResolver.ClassHierarchyInfo.ofClass(compiler.getHeapType(subtype.superTypes().getFirst()));
	}

	@Override
	protected byte[] generateImpl() {
		ImplClassGenerator implClass;
		if(subtype.isFinal()) {
			implClass = null;
		}
		else {
			implClass = new ImplClassGenerator();
			compiler.enqueueGenerator(implClass);
		}

		return compiler.classFile()
			.build(className, clb -> {
				var elementTypeRealization = compiler.getStorageType(arrayType.fieldType().storageType());
				ClassDesc superClass;
				if(subtype.superTypes().isEmpty()) {
					superClass = switch(arrayType.fieldType().mut()) {
						case Const -> wasmArrayImmutable;
						case Var -> wasmArrayMutable;
					};
				}
				else {
					superClass = compiler.getHeapType(subtype.superTypes().getFirst());
				}


				clb.withFlags(ClassFile.ACC_PUBLIC);
				clb.withSuperclass(superClass);

				if(subtype.isFinal()) {
					generateFinalArray(clb, className, superClass, elementTypeRealization);
					generateFactoryMethods(clb, className, className, elementTypeRealization);
				}
				else {
					generateAbstractMethods(clb, superClass, elementTypeRealization);
					generateFactoryMethods(clb, className, className.nested("Impl"), elementTypeRealization);
				}

				if(implClass != null) {
					clb.with(
						InnerClassesAttribute.of(
							InnerClassInfo.of(implClass.className(), Optional.of(className), Optional.of("Impl"), ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL)
						)
					);
					clb.with(
						NestMembersAttribute.ofSymbols(
							implClass.className()
						)
					);
				}
			});
	}

	private void generateFinalArray(ClassBuilder clb, ClassDesc thisClass, ClassDesc superClass, TypeRealization elementTypeRealization) {
		clb.withField("array", elementTypeRealization.type().arrayType(), ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);

		clb.withMethodBody(
			"<init>",
			MethodTypeDesc.of(CD_void, elementTypeRealization.type().arrayType()),
			ClassFile.ACC_PRIVATE,
			cb -> {
				cb.aload(0);
				cb.aload(1);
				cb.putfield(thisClass, "array", elementTypeRealization.type().arrayType());

				cb.aload(0);
				cb.invokespecial(superClass, "<init>", MethodTypeDesc.of(CD_void));
				cb.return_();
			}
		);


		clb.withMethodBody(
			"length",
			MethodTypeDesc.of(CD_int),
			ClassFile.ACC_PUBLIC,
			cb -> {
				cb.aload(0);
				cb.getfield(thisClass, "array", elementTypeRealization.type().arrayType());
				cb.arraylength();
				cb.ireturn();
			}
		);

		clb.withMethod(
			"get",
			MethodTypeDesc.of(elementTypeRealization.type(), CD_int),
			ClassFile.ACC_PUBLIC,
			mb -> {
				if(elementTypeRealization.isNullable()) {
					mb.with(
						RuntimeVisibleTypeAnnotationsAttribute.of(
							TypeAnnotation.of(
								TypeAnnotation.TargetInfo.ofMethodReturn(),
								List.of(),
								nullableAnn
							)
						)
					);
				}

				mb.withCode(cb -> {
					cb.aload(0);
					cb.getfield(thisClass, "array", elementTypeRealization.type().arrayType());
					cb.iload(1);
					cb.arrayLoad(typeKind(elementTypeRealization.type()));
					cb.return_(typeKind(elementTypeRealization.type()));
				});
			}
		);

		if(arrayType.fieldType().mut() == Mut.Const) {
			Set<ClassDesc> seenGetTypes = new HashSet<>();
			seenGetTypes.add(elementTypeRealization.type());

			for(
				var superTypeRealization = realization().superType().get();
				superTypeRealization != null;
				superTypeRealization = superTypeRealization.superType().get()
			) {
				var superFieldType = superTypeRealization.elementType().get();

				if(!seenGetTypes.add(superFieldType)) {
					continue;
				}


				clb.withMethodBody(
					"get",
					MethodTypeDesc.of(superFieldType, CD_int),
					ClassFile.ACC_PUBLIC | ClassFile.ACC_SYNTHETIC | ClassFile.ACC_BRIDGE,
					cb -> {
						cb.aload(0);
						cb.getfield(thisClass, "array", elementTypeRealization.type().arrayType());
						cb.iload(1);
						cb.arrayLoad(typeKind(elementTypeRealization.type()));
						cb.return_(typeKind(elementTypeRealization.type()));
					}
				);
			}
		}

		if(arrayType.fieldType().mut() == Mut.Var) {
			clb.withMethod(
				"set",
				MethodTypeDesc.of(CD_void, CD_int, elementTypeRealization.type()),
				ClassFile.ACC_PUBLIC,
				mb -> {
					if(elementTypeRealization.isNullable()) {
						mb.with(
							RuntimeVisibleTypeAnnotationsAttribute.of(
								TypeAnnotation.of(
									TypeAnnotation.TargetInfo.ofMethodFormalParameter(0),
									List.of(),
									nullableAnn
								)
							)
						);
					}

					mb.withCode(cb -> {
						cb.aload(0);
						cb.getfield(thisClass, "array", elementTypeRealization.type().arrayType());
						cb.iload(1);
						cb.loadLocal(typeKind(elementTypeRealization.type()), 2);
						cb.arrayStore(typeKind(elementTypeRealization.type()));
						cb.return_();

					});
				}
			);

			clb.withMethod(
				"fill",
				MethodTypeDesc.of(CD_void, CD_int, elementTypeRealization.type(), CD_int),
				ClassFile.ACC_PUBLIC,
				mb -> {
					if(elementTypeRealization.isNullable()) {
						mb.with(
							RuntimeVisibleTypeAnnotationsAttribute.of(
								TypeAnnotation.of(
									TypeAnnotation.TargetInfo.ofMethodFormalParameter(1),
									List.of(),
									nullableAnn
								)
							)
						);
					}

					mb.withCode(cb -> {
						cb.iload(1);
						cb.iload(2 + typeKind(elementTypeRealization.type()).slotSize());
						cb.aload(0);
						cb.getfield(thisClass, "array", elementTypeRealization.type().arrayType());
						cb.arraylength();
						cb.invokestatic(ClassDesc.of("java.util.Objects"), "checkFromIndexSize", MethodTypeDesc.of(CD_int, CD_int, CD_int, CD_int));
						cb.pop();


						cb.aload(0);
						cb.getfield(thisClass, "array", elementTypeRealization.type().arrayType());
						cb.iload(1);
						cb.iload(1);
						cb.iload(2 + typeKind(elementTypeRealization.type()).slotSize());
						cb.iadd();
						cb.loadLocal(typeKind(elementTypeRealization.type()), 2);
						if(elementTypeRealization.type().isPrimitive()) {
							cb.invokestatic(ClassDesc.of("java.util.Arrays"), "fill", MethodTypeDesc.of(CD_void, elementTypeRealization.type().arrayType(), CD_int, CD_int, elementTypeRealization.type()));
						}
						else {
							cb.invokestatic(ClassDesc.of("java.util.Arrays"), "fill", MethodTypeDesc.of(CD_void, CD_Object.arrayType(), CD_int, CD_int, CD_Object));
						}

						cb.return_();
					});
				}
			);

			if(isInitDataType(elementTypeRealization.type())) {
				clb.withMethodBody(
					"copyFromData",
					MethodTypeDesc.of(CD_void, CD_int, CD_int, CD_int, CD_byte.arrayType()),
					ClassFile.ACC_PUBLIC,
					cb -> {
						cb.aload(0);
						cb.getfield(thisClass, "array", elementTypeRealization.type().arrayType());
						cb.iload(1);
						cb.aload(4);
						cb.iload(2);
						cb.iload(3);
						cb.invokestatic(
							wasmArray,
							"initArrayFromData",
							MethodTypeDesc.of(CD_void, elementTypeRealization.type().arrayType(), CD_int, CD_byte.arrayType(), CD_int, CD_int)
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
				cb.getfield(thisClass, "array", elementTypeRealization.type().arrayType());
				cb.areturn();
			}
		);

	}

	private void generateAbstractMethods(ClassBuilder clb, ClassDesc superClass, TypeRealization elementTypeRealization) {
		clb.withMethodBody(
			"<init>",
			MethodTypeDesc.of(CD_void),
			ClassFile.ACC_PRIVATE,
			cb -> {
				cb.aload(0);
				cb.invokespecial(superClass, "<init>", MethodTypeDesc.of(CD_void));
				cb.return_();
			}
		);

		clb.withMethod(
			"get",
			MethodTypeDesc.of(elementTypeRealization.type(), CD_int),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT,
			mb -> {
				if(elementTypeRealization.isNullable()) {
					mb.with(
						RuntimeVisibleTypeAnnotationsAttribute.of(
							TypeAnnotation.of(
								TypeAnnotation.TargetInfo.ofMethodReturn(),
								List.of(),
								nullableAnn
							)
						)
					);
				}
			}
		);

		if(arrayType.fieldType().mut() == Mut.Var) {
			clb.withMethod(
				"set",
				MethodTypeDesc.of(CD_void, CD_int, elementTypeRealization.type()),
				ClassFile.ACC_PUBLIC,
				mb -> {
					if(elementTypeRealization.isNullable()) {
						mb.with(
							RuntimeVisibleTypeAnnotationsAttribute.of(
								TypeAnnotation.of(
									TypeAnnotation.TargetInfo.ofMethodFormalParameter(0),
									List.of(),
									nullableAnn
								)
							)
						);
					}
				}
			);

			clb.withMethod(
				"fill",
				MethodTypeDesc.of(CD_void, CD_int, elementTypeRealization.type(), CD_int),
				ClassFile.ACC_PUBLIC,
				mb -> {
					if(elementTypeRealization.isNullable()) {
						mb.with(
							RuntimeVisibleTypeAnnotationsAttribute.of(
								TypeAnnotation.of(
									TypeAnnotation.TargetInfo.ofMethodFormalParameter(1),
									List.of(),
									nullableAnn
								)
							)
						);
					}
				}
			);

			if(isInitDataType(elementTypeRealization.type())) {
				clb.withMethod(
					"copyFromData",
					MethodTypeDesc.of(CD_void, CD_int, CD_int, CD_int, CD_byte.arrayType()),
					ClassFile.ACC_PUBLIC,
					mb -> {}
				);
			}
		}
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

	private void generateFactoryMethods(ClassBuilder clb, ClassDesc thisClass, ClassDesc implClass, TypeRealization elementTypeRealization) {
		clb.withMethodBody(
			"nCopies",
			MethodTypeDesc.of(thisClass, elementTypeRealization.type(), CD_int),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
			cb -> {
				cb.new_(implClass);
				cb.dup();

				cb.iload(typeKind(elementTypeRealization.type()).slotSize());
				if(elementTypeRealization.type().isPrimitive()) {
					cb.newarray(typeKind(elementTypeRealization.type()));
				}
				else {
					cb.anewarray(elementTypeRealization.type());
				}
				cb.dup();
				cb.loadLocal(typeKind(elementTypeRealization.type()), 0);
				if(elementTypeRealization.type().isPrimitive()) {
					cb.invokestatic(ClassDesc.of("java.util.Arrays"), "fill", MethodTypeDesc.of(CD_void, elementTypeRealization.type().arrayType(), elementTypeRealization.type()));
				}
				else {
					cb.invokestatic(ClassDesc.of("java.util.Arrays"), "fill", MethodTypeDesc.of(CD_void, CD_Object.arrayType(), CD_Object));
				}

				cb.invokespecial(implClass, "<init>", MethodTypeDesc.of(CD_void, elementTypeRealization.type().arrayType()));
				cb.areturn();
			}
		);

		if(isDefaultableDataType(arrayType.fieldType().storageType())) {
			clb.withMethodBody(
				"ofDefault",
				MethodTypeDesc.of(thisClass, CD_int),
				ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
				cb -> {
					if(elementTypeRealization.type().equals(v128Type)) {
						cb.iload(0);
						loadV128(cb, V128.ZERO);
						cb.invokestatic(
							thisClass,
							"nCopies",
							MethodTypeDesc.of(thisClass, v128Type, CD_int)
						);
						cb.areturn();
					}
					else {
						cb.new_(implClass);
						cb.dup();

						cb.iload(0);
						if(elementTypeRealization.type().isPrimitive()) {
							cb.newarray(typeKind(elementTypeRealization.type()));
						}
						else {
							cb.anewarray(elementTypeRealization.type());
						}

						cb.invokespecial(implClass, "<init>", MethodTypeDesc.of(CD_void, elementTypeRealization.type().arrayType()));
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
				cb.new_(implClass);
				cb.dup();

				cb.iconst_0();
				if(elementTypeRealization.type().isPrimitive()) {
					cb.newarray(typeKind(elementTypeRealization.type()));
				}
				else {
					cb.anewarray(elementTypeRealization.type());
				}

				cb.invokespecial(implClass, "<init>", MethodTypeDesc.of(CD_void, elementTypeRealization.type().arrayType()));
				cb.areturn();
			}
		);

		// Copy from another array.
		clb.withMethod(
			"copyOfJavaArray",
			MethodTypeDesc.of(thisClass, CD_int, CD_int, elementTypeRealization.type().arrayType()),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
			mb -> {
				if(elementTypeRealization.isNullable()) {
					mb.with(
						RuntimeVisibleTypeAnnotationsAttribute.of(
							TypeAnnotation.of(
								TypeAnnotation.TargetInfo.ofMethodFormalParameter(2),
								List.of(TypeAnnotation.TypePathComponent.of(TypeAnnotation.TypePathComponent.Kind.ARRAY, 0)),
								nullableAnn
							)
						)
					);
				}

				mb.withCode(cb -> {
					// Create the Array.
					cb.iload(1);
					if(elementTypeRealization.type().isPrimitive()) {
						cb.newarray(typeKind(elementTypeRealization.type()));
					}
					else {
						cb.anewarray(elementTypeRealization.type());
					}
					cb.astore(3);

					// arraycopy from input array
					cb.aload(2);
					cb.iload(0);
					cb.aload(3);
					cb.iconst_0();
					cb.iload(1);
					cb.invokestatic(ClassDesc.of("java.lang.System"), "arraycopy", MethodTypeDesc.of(CD_void, CD_Object, CD_int, CD_Object, CD_int, CD_int));

					cb.new_(implClass);
					cb.dup();
					cb.aload(3);
					cb.invokespecial(implClass, "<init>", MethodTypeDesc.of(CD_void, elementTypeRealization.type().arrayType()));
					cb.areturn();
				});
			}
		);

		// Calls T[] with 0 and length
		clb.withMethod(
			"copyOfJavaArray",
			MethodTypeDesc.of(thisClass, elementTypeRealization.type().arrayType()),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
			mb -> {
				if(elementTypeRealization.isNullable()) {
					mb.with(
						RuntimeVisibleTypeAnnotationsAttribute.of(
							TypeAnnotation.of(
								TypeAnnotation.TargetInfo.ofMethodFormalParameter(0),
								List.of(TypeAnnotation.TypePathComponent.of(TypeAnnotation.TypePathComponent.Kind.ARRAY, 0)),
								nullableAnn
							)
						)
					);
				}


				mb.withCode(cb -> {
					cb.iconst_0();
					cb.aload(0);
					cb.arraylength();
					cb.aload(0);
					cb.invokestatic(thisClass, "copyOfJavaArray", MethodTypeDesc.of(thisClass, CD_int, CD_int, elementTypeRealization.type().arrayType()));
					cb.areturn();
				});
			}
		);

		generateNewDataFactoryMethods(clb, thisClass, implClass, elementTypeRealization);
	}

	private void generateNewDataFactoryMethods(ClassBuilder clb, ClassDesc thisClass, ClassDesc implClass, TypeRealization elementTypeRealization) {
		if(!isInitDataType(elementTypeRealization.type())) {
			return;
		}

		clb.withMethodBody(
			"copyOfData",
			MethodTypeDesc.of(thisClass, CD_int, CD_int, CD_byte.arrayType()),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
			cb -> {
				// Create the Array.
				cb.iload(1);
				if(elementTypeRealization.type().isPrimitive()) {
					cb.newarray(typeKind(elementTypeRealization.type()));
				}
				else {
					cb.anewarray(elementTypeRealization.type());
				}
				cb.astore(3);

				cb.aload(3);
				cb.iconst_0();
				cb.aload(2);
				cb.iload(0);
				cb.iload(1);
				cb.invokestatic(wasmArray, "initArrayFromData", MethodTypeDesc.of(CD_void, elementTypeRealization.type().arrayType(), CD_int, CD_byte.arrayType(), CD_int, CD_int));

				cb.new_(implClass);
				cb.dup();
				cb.aload(3);
				cb.invokespecial(implClass, "<init>", MethodTypeDesc.of(CD_void, elementTypeRealization.type().arrayType()));
				cb.areturn();
			}
		);
	}

	private class ImplClassGenerator extends WasmClassGenerator {
		ImplClassGenerator() {
			super(ArrayClassGenerator.this.compiler);
		}

		private final ClassDesc className = ArrayClassGenerator.this.className.nested("Impl");

		@Override
		public ClassDesc className() {
			return className;
		}

		@Override
		public ClassHierarchyResolver.ClassHierarchyInfo hierarchyInfo() {
			return ClassHierarchyResolver.ClassHierarchyInfo.ofClass(CD_Object);
		}

		@Override
		byte[] generateImpl() {
			return compiler.classFile()
				.build(className, clb -> {
					var elementTypeRealization = compiler.getStorageType(arrayType.fieldType().storageType());

					clb.withInterfaceSymbols(ArrayClassGenerator.this.className);
					clb.withFlags(ClassFile.ACC_FINAL);
					generateFinalArray(clb, className, ArrayClassGenerator.this.className, elementTypeRealization);

					clb.with(NestHostAttribute.of(ArrayClassGenerator.this.className));
					clb.with(
						InnerClassesAttribute.of(
							InnerClassInfo.of(className(), Optional.of(ArrayClassGenerator.this.className), Optional.of("Impl"), ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL)
						)
					);
				});
		}
	}
}
