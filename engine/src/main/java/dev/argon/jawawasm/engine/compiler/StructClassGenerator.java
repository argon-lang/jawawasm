package dev.argon.jawawasm.engine.compiler;

import dev.argon.jawawasm.format.types.Mut;
import dev.argon.jawawasm.format.types.StructType;
import dev.argon.jawawasm.format.types.SubType;
import org.jspecify.annotations.Nullable;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static dev.argon.jawawasm.engine.compiler.Constants.RUNTIME_PACKAGE;
import static dev.argon.jawawasm.engine.compiler.WasmClassGeneratorUtils.typeKind;
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
		if(!subtype.superTypes().isEmpty() || !subtype.isFinal()) {
			throw new RuntimeException("Not implemented");
		}

		return ClassHierarchyResolver.ClassHierarchyInfo.ofClass(CD_Object);
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
			List.of(fields)
		);
	}

	@Override
	protected byte[] generateImpl() {
		if(!subtype.superTypes().isEmpty() || !subtype.isFinal()) {
			throw new RuntimeException("Not implemented");
		}

		var superInterface = ClassDesc.of(RUNTIME_PACKAGE, "WasmStruct");

		return compiler.classFile()
			.build(className, clb -> generateFinalStruct(clb, className, superInterface));
	}

	private void generateFinalStruct(ClassBuilder clb, ClassDesc thisClass, @Nullable ClassDesc superInterface) {

		var fields = structType.fields();

		clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL);
		clb.withInterfaceSymbols(superInterface);

		TypeRealization[] fieldTypes = new TypeRealization[fields.size()];
		List<ClassDesc> constructorArgs = new ArrayList<>();

		for(int i = 0; i < fields.size(); ++i) {
			var fieldType = fields.get(i);
			var t = compiler.getStorageType(fieldType.storageType());
			var tk = typeKind(t.type());
			fieldTypes[i] = t;
			constructorArgs.add(t.type());

			int flags = ClassFile.ACC_PRIVATE;
			if(fieldType.mut() == Mut.Var) {
				flags |= ClassFile.ACC_FINAL;
			}

			var fieldName = "field" + i;

			clb.withField(fieldName, t.type(), flags);


			clb.withMethodBody(
				"get" + i,
				MethodTypeDesc.of(t.type()),
				ClassFile.ACC_PUBLIC,
				cb -> {
					cb.aload(0);
					cb.getfield(thisClass, fieldName, t.type());
					cb.return_(tk);
				}
			);

			if(fieldType.mut() == Mut.Var) {
				clb.withMethodBody(
					"set" + i,
					MethodTypeDesc.of(CD_void, t.type()),
					ClassFile.ACC_PUBLIC,
					cb -> {
						cb.aload(0);
						cb.loadLocal(tk, 2);
						cb.putfield(thisClass, fieldName, t.type());
						cb.return_();
					}
				);
			}
		}


		clb.withMethodBody(
			"<init>",
			MethodTypeDesc.of(CD_void, constructorArgs),
			ClassFile.ACC_PRIVATE,
			cb -> {
				int slot = 1;
				for(int i = 0; i < fields.size(); ++i) {
					var t = fieldTypes[i].type();
					var tk = typeKind(t);

					cb.aload(0);
					cb.loadLocal(tk, slot);

					cb.putfield(thisClass, "field" + i, t);

					slot += tk.slotSize();
				}

				cb.aload(0);
				cb.invokespecial(CD_Object, "<init>", MethodTypeDesc.of(CD_void));
				cb.return_();
			}
		);

		clb.withMethodBody(
			"create",
			MethodTypeDesc.of(className, constructorArgs),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
			cb -> {
				cb.new_(className);
				cb.dup();
				cb.dup();

				int slot = 0;
				for(int i = 0; i < fields.size(); ++i) {
					var t = fieldTypes[i].type();
					var tk = typeKind(t);

					cb.loadLocal(tk, slot);

					slot += tk.slotSize();
				}

				cb.invokespecial(className, "<init>", MethodTypeDesc.of(CD_void, constructorArgs));
				cb.areturn();
			}
		);


	}
}
