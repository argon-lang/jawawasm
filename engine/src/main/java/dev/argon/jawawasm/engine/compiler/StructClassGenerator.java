package dev.argon.jawawasm.engine.compiler;

import dev.argon.jawawasm.format.types.DefType;
import dev.argon.jawawasm.format.types.Mut;
import dev.argon.jawawasm.format.types.StructType;
import dev.argon.jawawasm.format.types.SubType;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.TypeAnnotation;
import java.lang.classfile.attribute.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.*;
import java.util.function.Supplier;

import static dev.argon.jawawasm.engine.compiler.WasmClassGeneratorUtils.*;
import static java.lang.constant.ConstantDescs.*;

class StructClassGenerator extends DefTypeClassGenerator {
	public StructClassGenerator(ModuleCompiler compiler, SubType subtype, StructType structType, String className) {
		super(compiler);
		this.subtype = subtype;
		this.structType = structType;
		this.className = ClassDesc.of(compiler.getOptions().javaPackage(), className);
	}

	private final SubType subtype;
	private final StructType structType;
	private final ClassDesc className;

	@Override
	public ClassDesc className() {
		return className;
	}

	@Override
	public ClassHierarchyResolver.ClassHierarchyInfo hierarchyInfo() {
		if(subtype.isFinal()) {
			return ClassHierarchyResolver.ClassHierarchyInfo.ofClass(CD_Object);
		}
		else {
			return ClassHierarchyResolver.ClassHierarchyInfo.ofInterface();
		}

	}

	@Override
	public StructTypeRealization realization() {
		var fields = new StructTypeRealization.Field[structType.fields().size()];
		for(int i = 0; i < fields.length; ++i) {
			var fieldType = structType.fields().get(i);
			Supplier<ClassDesc> t = () -> compiler.getStorageType(fieldType.storageType()).type();

			fields[i] = new StructTypeRealization.Field(
				t,
				new StructTypeRealization.AccessMethod(
					"get" + i,
					() -> MethodTypeDesc.of(t.get())
				),
				switch(fieldType.mut()) {
					case Const -> null;
					case Var -> new StructTypeRealization.AccessMethod(
						"set" + i,
						() -> MethodTypeDesc.of(CD_void, t.get())
					);
				}
			);
		}

		return new StructTypeRealization(
			className,
			!subtype.isFinal(),
			"create",
			() -> {
				var paramTypes = structType.fields().stream()
					.map(fieldType -> compiler.getStorageType(fieldType.storageType()).type())
					.toList();

				return MethodTypeDesc.of(className, paramTypes);
			},
			List.of(fields),
			() -> {
				if(subtype.superTypes().isEmpty()) {
					return null;
				}

				return (StructTypeRealization)compiler.getDefType((DefType)subtype.superTypes().getFirst());
			}
		);
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
				if(subtype.superTypes().isEmpty()) {
					clb.withInterfaceSymbols(wasmStruct);
				}
				else {
					clb.withInterfaceSymbols(
						subtype.superTypes()
							.stream()
							.map(compiler::getHeapType)
							.toList()
					);
				}

				int flags = ClassFile.ACC_PUBLIC;
				if(subtype.isFinal()) {
					flags |= ClassFile.ACC_FINAL;
				}
				else {
					flags |= ClassFile.ACC_ABSTRACT | ClassFile.ACC_INTERFACE;
				}

				clb.withFlags(flags);

				if(subtype.isFinal()) {
					generateFinalStruct(clb, className);
					generateFactoryMethods(clb, className, className);
				}
				else {
					generateAbstractMethods(clb);
					generateFactoryMethods(clb, className, className.nested("Impl"));
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

	private void generateFinalStruct(ClassBuilder clb, ClassDesc implClass) {
		var fields = structType.fields();

		TypeRealization[] fieldTypes = new TypeRealization[fields.size()];
		List<ClassDesc> constructorParams = new ArrayList<>();

		for(int i = 0; i < fields.size(); ++i) {
			var fieldType = fields.get(i);
			var t = compiler.getStorageType(fieldType.storageType());
			var tk = typeKind(t.type());
			fieldTypes[i] = t;
			constructorParams.add(t.type());

			int flags = ClassFile.ACC_PRIVATE;
			if(fieldType.mut() == Mut.Const) {
				flags |= ClassFile.ACC_FINAL;
			}

			var fieldName = "field" + i;

			clb.withField(fieldName, t.type(), flags);


			clb.withMethod(
				"get" + i,
				MethodTypeDesc.of(t.type()),
				ClassFile.ACC_PUBLIC,
				mb -> {
					if(t.isNullable()) {
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
						cb.getfield(implClass, fieldName, t.type());
						cb.return_(tk);
					});
				}
			);

			if(fieldType.mut() == Mut.Const) {
				Set<ClassDesc> seenGetTypes = new HashSet<>();
				seenGetTypes.add(t.type());

				for(
					var superTypeRealization = realization().superType().get();
					superTypeRealization != null;
					superTypeRealization = superTypeRealization.superType().get()
				) {
					if(i >= superTypeRealization.fields().size()) {
						break;
					}

					var superFieldType = superTypeRealization.fields().get(i).fieldType().get();

					if(!seenGetTypes.add(superFieldType)) {
						continue;
					}


					clb.withMethodBody(
						"get" + i,
						MethodTypeDesc.of(superFieldType),
						ClassFile.ACC_PUBLIC | ClassFile.ACC_SYNTHETIC | ClassFile.ACC_BRIDGE,
						cb -> {
							cb.aload(0);
							cb.getfield(implClass, fieldName, t.type());
							cb.return_(tk);
						}
					);
				}
			}


			if(fieldType.mut() == Mut.Var) {
				clb.withMethod(
					"set" + i,
					MethodTypeDesc.of(CD_void, t.type()),
					ClassFile.ACC_PUBLIC,
					mb -> {
						if(t.isNullable()) {
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
							cb.loadLocal(tk, 1);
							cb.putfield(implClass, fieldName, t.type());
							cb.return_();
						});
					}
				);
			}
		}


		clb.withMethodBody(
			"<init>",
			MethodTypeDesc.of(CD_void, constructorParams),
			ClassFile.ACC_PRIVATE,
			cb -> {
				int slot = 1;
				for(int i = 0; i < fields.size(); ++i) {
					var t = fieldTypes[i].type();
					var tk = typeKind(t);

					cb.aload(0);
					cb.loadLocal(tk, slot);

					cb.putfield(implClass, "field" + i, t);

					slot += tk.slotSize();
				}

				cb.aload(0);
				cb.invokespecial(CD_Object, "<init>", MethodTypeDesc.of(CD_void));
				cb.return_();
			}
		);
	}

	private void generateAbstractMethods(ClassBuilder clb) {
		var fields = structType.fields();

		for(int i = 0; i < fields.size(); ++i) {
			var fieldType = fields.get(i);
			var t = compiler.getStorageType(fieldType.storageType());

			clb.withMethod(
				"get" + i,
				MethodTypeDesc.of(t.type()),
				ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT,
				mb -> {
					if(t.isNullable()) {
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

			if(fieldType.mut() == Mut.Var) {
				clb.withMethodBody(
					"set" + i,
					MethodTypeDesc.of(CD_void, t.type()),
					ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT,
					mb -> {
						if(t.isNullable()) {
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
			}
		}
	}

	private void generateFactoryMethods(ClassBuilder clb, ClassDesc publicClass, ClassDesc implClass) {
		var fields = structType.fields();
		TypeRealization[] fieldTypes = new TypeRealization[fields.size()];
		List<ClassDesc> constructorParams = new ArrayList<>();

		for(int i = 0; i < fields.size(); ++i) {
			var fieldType = fields.get(i);
			var t = compiler.getStorageType(fieldType.storageType());
			fieldTypes[i] = t;
			constructorParams.add(t.type());
		}


		clb.withMethodBody(
			"create",
			MethodTypeDesc.of(publicClass, constructorParams),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
			cb -> {
				cb.new_(implClass);
				cb.dup();
				cb.dup();

				int slot = 0;
				for(int i = 0; i < fields.size(); ++i) {
					var t = fieldTypes[i].type();
					var tk = typeKind(t);

					cb.loadLocal(tk, slot);

					slot += tk.slotSize();
				}

				cb.invokespecial(implClass, "<init>", MethodTypeDesc.of(CD_void, constructorParams));
				cb.areturn();
			}
		);
	}

	private class ImplClassGenerator extends WasmClassGenerator {
		ImplClassGenerator() {
			super(StructClassGenerator.this.compiler);
		}

		private final ClassDesc className = StructClassGenerator.this.className.nested("Impl");

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
					clb.withInterfaceSymbols(StructClassGenerator.this.className);
					clb.withFlags(ClassFile.ACC_FINAL);
					generateFinalStruct(clb, className);


					clb.with(NestHostAttribute.of(StructClassGenerator.this.className));
					clb.with(
						InnerClassesAttribute.of(
							InnerClassInfo.of(className(), Optional.of(StructClassGenerator.this.className), Optional.of("Impl"), ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL)
						)
					);
				});
		}
	}
}
