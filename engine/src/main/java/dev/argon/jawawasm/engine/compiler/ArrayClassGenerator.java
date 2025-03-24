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
			!subtype.isFinal(),
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

		var superClass = switch(arrayType.fieldType().mut()) {
			case Const -> wasmArrayImmutable;
			case Var -> wasmArrayMutable;
		};

		return compiler.classFile()
			.build(className, clb -> generateFinalArray(clb, className, superClass));
	}

	private void generateFinalArray(ClassBuilder clb, ClassDesc thisClass, @Nullable ClassDesc superClass) {
		var elementTypeRealization = compiler.getStorageType(arrayType.fieldType().storageType()).type();

		clb.withFlags(ClassFile.ACC_PUBLIC);
		clb.withSuperclass(superClass);

		clb.withField("array", elementTypeRealization.arrayType(), ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);

		// Constructor with initial value.
		clb.withMethodBody(
			"<init>",
			MethodTypeDesc.of(CD_void, CD_int, elementTypeRealization),
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
				cb.dup();
				cb.loadLocal(typeKind(elementTypeRealization), 2);
				if(elementTypeRealization.isPrimitive()) {
					cb.invokestatic(ClassDesc.of("java.util.Arrays"), "fill", MethodTypeDesc.of(CD_void, elementTypeRealization.arrayType(), elementTypeRealization));
				}
				else {
					cb.invokestatic(ClassDesc.of("java.util.Arrays"), "fill", MethodTypeDesc.of(CD_void, CD_Object.arrayType(), CD_Object));
				}
				cb.putfield(thisClass, "array", elementTypeRealization.arrayType());

				cb.aload(0);
				cb.invokespecial(superClass, "<init>", MethodTypeDesc.of(CD_void));
				cb.return_();
			}
		);

		// Constructor for empty array.
		clb.withMethodBody(
			"<init>",
			MethodTypeDesc.of(CD_void),
			ClassFile.ACC_PUBLIC,
			cb -> {
				cb.aload(0);
				cb.iconst_0();
				if(elementTypeRealization.isPrimitive()) {
					cb.newarray(typeKind(elementTypeRealization));
				}
				else {
					cb.anewarray(elementTypeRealization);
				}
				cb.putfield(thisClass, "array", elementTypeRealization.arrayType());

				cb.aload(0);
				cb.invokespecial(superClass, "<init>", MethodTypeDesc.of(CD_void));
				cb.return_();
			}
		);

		// For primitive types, we want to have a constructor overload that copies from a byte array.
		// We will generate one for byte[] anyway, so skip that.
		if(elementTypeRealization.isPrimitive() && elementTypeRealization != CD_byte) {
			int byteSize;
			int shiftFactor;
			if(elementTypeRealization == CD_short) {
				byteSize = 2;
				shiftFactor = 1;
			}
			else if(elementTypeRealization == CD_int || elementTypeRealization == CD_float) {
				byteSize = 4;
				shiftFactor = 2;
			}
			else if(elementTypeRealization == CD_long || elementTypeRealization == CD_double) {
				byteSize = 8;
				shiftFactor = 3;
			}
			else {
				throw new RuntimeException("Unexpected primitive type for array.");
			}

			clb.withMethodBody(
				"<init>",
				MethodTypeDesc.of(CD_void, CD_byte.arrayType(), CD_int, CD_int),
				ClassFile.ACC_PUBLIC,
				cb -> {
					// Create the Array.
					cb.iload(3);
					cb.newarray(typeKind(elementTypeRealization));
					cb.astore(4);

					// java.util.Objects.checkFromIndexSize(s, n, data.length / byteSize);
					cb.iload(2);
					cb.iload(3);
					cb.aload(1);
					cb.arraylength();
					cb.loadConstant(shiftFactor);
					cb.ishr();
					cb.invokestatic(ClassDesc.of("java.util.Objects"), "checkFromIndexSize", MethodTypeDesc.of(CD_int, CD_int, CD_int, CD_int));
					cb.pop();

					// for(int i = 0; i < n; ++i)
					var loopStart = cb.newLabel();
					var loopEnd = cb.newLabel();
					cb.iconst_0();
					cb.istore(5);
					cb.labelBinding(loopStart);
					cb.iload(5);
					cb.iload(3);
					cb.isub();
					cb.ifge(loopEnd);

					// a[i] = (data[s + i * byteSize] & 0xFF) | ((data[s + i * byteSize + 1] & 0xFF) << 8) | ...
					cb.aload(4);
					cb.iload(5);
					for(int i = 0; i < byteSize; ++i) {
						cb.aload(1);
						cb.iload(5);
						cb.loadConstant(shiftFactor);
						cb.ishl();
						cb.iload(3);
						cb.iadd();
						if(i > 0) {
							cb.loadConstant(i);
							cb.iadd();
						}
						cb.baload();

						if(byteSize == 8) {
							cb.loadConstant(0xFFL);
							cb.land();
							if(i > 0) {
								cb.loadConstant(8 * i);
								cb.lshl();
								cb.lor();
							}

						}
						else {
							cb.loadConstant(0xFF);
							cb.iand();
							if(i > 0) {
								cb.loadConstant(8 * i);
								cb.ishl();
								cb.ior();
							}

						}
					}
					if(elementTypeRealization == CD_float) {
						cb.invokestatic(CD_Float, "intBitsToFloat", MethodTypeDesc.of(CD_float, CD_int));
					}
					else if(elementTypeRealization == CD_double) {
						cb.invokestatic(CD_Double, "longBitsToDouble", MethodTypeDesc.of(CD_double, CD_long));
					}
					cb.arrayStore(typeKind(elementTypeRealization));

					// end loop
					cb.goto_(loopStart);
					cb.labelBinding(loopEnd);


					cb.aload(0);
					cb.aload(4);
					cb.putfield(thisClass, "array", elementTypeRealization.arrayType());

					cb.aload(0);
					cb.invokespecial(superClass, "<init>", MethodTypeDesc.of(CD_void));
					cb.return_();
				}
			);

			// Calls byte[] constructor with 0 and length
			clb.withMethodBody(
				"<init>",
				MethodTypeDesc.of(CD_void, CD_byte.arrayType()),
				ClassFile.ACC_PUBLIC,
				cb -> {
					cb.aload(0);
					cb.aload(1);
					cb.iconst_0();
					cb.aload(1);
					cb.arraylength();
					cb.invokespecial(className, "<init>", MethodTypeDesc.of(CD_void, CD_byte.arrayType(), CD_int, CD_int));
					cb.return_();
				}
			);
		}

		// Copy from another array.
		clb.withMethodBody(
			"<init>",
			MethodTypeDesc.of(CD_void, elementTypeRealization.arrayType(), CD_int, CD_int),
			ClassFile.ACC_PUBLIC,
			cb -> {
				// Create the Array.
				cb.iload(3);
				if(elementTypeRealization.isPrimitive()) {
					cb.newarray(typeKind(elementTypeRealization));
				}
				else {
					cb.anewarray(elementTypeRealization);
				}
				cb.astore(4);

				// arraycopy from input array
				cb.aload(1);
				cb.iload(2);
				cb.aload(4);
				cb.iconst_0();
				cb.iload(3);
				cb.invokestatic(ClassDesc.of("java.lang.System"), "arraycopy", MethodTypeDesc.of(CD_void, CD_Object, CD_int, CD_Object, CD_int, CD_int));

				cb.aload(0);
				cb.aload(4);
				cb.putfield(thisClass, "array", elementTypeRealization.arrayType());

				cb.aload(0);
				cb.invokespecial(superClass, "<init>", MethodTypeDesc.of(CD_void));
				cb.return_();
			}
		);

		// Calls T[] with 0 and length
		clb.withMethodBody(
			"<init>",
			MethodTypeDesc.of(CD_void, elementTypeRealization.arrayType()),
			ClassFile.ACC_PUBLIC,
			cb -> {
				cb.aload(0);
				cb.aload(1);
				cb.iconst_0();
				cb.aload(1);
				cb.arraylength();
				cb.invokespecial(className, "<init>", MethodTypeDesc.of(CD_void, elementTypeRealization.arrayType(), CD_int, CD_int));
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
		}

		clb.withMethodBody(
			"unsafeGetArray",
			MethodTypeDesc.of(CD_Object),
			ClassFile.ACC_PUBLIC,
			cb -> {
				cb.aload(0);
				cb.getfield(thisClass, "array", elementTypeRealization.arrayType());
				cb.areturn();
			}
		);

	}
}
