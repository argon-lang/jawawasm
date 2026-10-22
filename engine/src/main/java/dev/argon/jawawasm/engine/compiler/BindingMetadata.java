package dev.argon.jawawasm.engine.compiler;

import dev.argon.jawawasm.format.types.*;
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.constant.*;
import java.util.*;
import static dev.argon.jawawasm.engine.compiler.TypeAnnotations.*;

final class BindingMetadata {
	private BindingMetadata() {}

	static List<TypeAnnotation> typeUse(
		StorageType type,
		TypeAnnotation.TargetInfo target,
		List<TypeAnnotation.TypePathComponent> path
	) {
		var result = new ArrayList<TypeAnnotation>();
		if(type instanceof RefType ref) {
			if(ref.isNullable())
				result.add(
					TypeAnnotation.of(target, path, Annotation.of(ClassDesc.of("org.jspecify.annotations.Nullable")))
				);
			if(ref.heapType() == HeapType.AbstractHeapType.EXTERN
				|| ref.heapType() == HeapType.AbstractHeapType.NOEXTERN)
				result.add(TypeAnnotation.of(target, path, Annotation.of(annotationClass("ExternRef"))));
			if(Set.of(
				HeapType.AbstractHeapType.NONE,
				HeapType.AbstractHeapType.NOFUNC,
				HeapType.AbstractHeapType.NOEXTERN,
				HeapType.AbstractHeapType.NOEXN
			).contains(ref.heapType()))
				result.add(TypeAnnotation.of(target, path, Annotation.of(annotationClass("NoneType"))));
		}
		return result;
	}

	static byte[] struct(ModuleCompiler compiler, byte[] bytes, StructType type) {
		var model = compiler.classFile().parse(bytes);
		return compiler.classFile().transformClass(model, (clb, e) -> {
			if(e instanceof MethodModel m
				&& (m.methodName().stringValue().matches("get[0-9]+")
					|| m.methodName().stringValue().matches("set[0-9]+"))
				&& (m.flags().flagsMask() & ClassFile.ACC_BRIDGE) == 0) {
				boolean get = m.methodName().stringValue().startsWith("get");
				int index = Integer.parseInt(m.methodName().stringValue().substring(3));
				var field = type.fields().get(index);
				clb.transformMethod(
					m,
					MethodTransform.dropping(x -> x instanceof RuntimeVisibleTypeAnnotationsAttribute)
						.andThen(MethodTransform.endHandler(mb -> {
							mb.with(
								RuntimeVisibleAnnotationsAttribute.of(
									Annotation.of(
										annotationClass(get ? "FieldAccessor" : "FieldMutator"),
										List.of(element("value", AnnotationValue.ofInt(index)))
									)
								)
							);
							mb.with(
								RuntimeVisibleTypeAnnotationsAttribute.of(
									typeUse(
										field.storageType(),
										get
											? TypeAnnotation.TargetInfo.ofMethodReturn()
											: TypeAnnotation.TargetInfo.ofMethodFormalParameter(0),
										List.of()
									)
								)
							);
						}))
				);
			}
			else
				clb.with(e);
		});
	}

	static byte[] array(ModuleCompiler compiler, byte[] bytes, ArrayType type) {
		return compiler.classFile().transformClass(compiler.classFile().parse(bytes), (clb, element) -> {
			if(element instanceof MethodModel method && !method.flags().has(java.lang.reflect.AccessFlag.BRIDGE)) {
				var name = method.methodName().stringValue();
				boolean get = name.equals("get");
				boolean set = name.equals("set");
				if(get || set || name.equals("fill")) {
					clb.transformMethod(
						method,
						MethodTransform.dropping(e -> e instanceof RuntimeVisibleTypeAnnotationsAttribute)
							.andThen(MethodTransform.endHandler(mb -> {
								if(get || set)
									mb.with(
										RuntimeVisibleAnnotationsAttribute.of(
											Annotation.of(
												annotationClass(get ? "FieldAccessor" : "FieldMutator"),
												List.of(element("value", AnnotationValue.ofInt(0)))
											)
										)
									);
								mb.with(
									RuntimeVisibleTypeAnnotationsAttribute.of(
										typeUse(
											type.fieldType().storageType(),
											get
												? TypeAnnotation.TargetInfo.ofMethodReturn()
												: TypeAnnotation.TargetInfo.ofMethodFormalParameter(1),
											List.of()
										)
									)
								);
							}))
					);
					return;
				}
			}
			clb.with(element);
		});
	}

	static byte[] module(ModuleCompiler compiler, byte[] bytes, List<WasmExportRealization> exports) {
		var tags = new ArrayList<AnnotationValue>();
		for(var export : exports)
			if(export instanceof WasmExportRealization.OfInnerClass tag) {
				tags.add(
					AnnotationValue.ofAnnotation(
						Annotation.of(
							annotationClass("WasmTagExport"),
							List.of(
								element("name", AnnotationValue.ofString(tag.exportName())),
								element("exception", AnnotationValue.ofClass(tag.classDesc())),
								element(
									"functionType",
									AnnotationValue.ofClass(compiler.getDefType(tag.tagDefType()).classDesc())
								)
							)
						)
					)
				);
			}
		var model = compiler.classFile().parse(bytes);
		return compiler.classFile().transformClass(model, ClassTransform.ofStateful(() -> new ClassTransform() {
			@Override
			public void accept(ClassBuilder clb, ClassElement e) {
				if(e instanceof MethodModel m) {
					var export = exports.stream()
						.filter(
							x -> x instanceof WasmExportRealization.OfInstanceMethod f
								&& f.methodName().equals(m.methodName().stringValue())
								&& f.methodType().equals(m.methodTypeSymbol())
						).map(x -> (WasmExportRealization.OfInstanceMethod)x).findFirst();
					if(export.isPresent()) {
						clb.transformMethod(
							m,
							MethodTransform.dropping(
								x -> x instanceof SignatureAttribute
									|| x instanceof RuntimeVisibleTypeAnnotationsAttribute
							).andThen(MethodTransform.endHandler(mb -> decorate(compiler, mb, export.get())))
						);
						return;
					}
				}
				clb.with(e);
			}

			@Override
			public void atEnd(ClassBuilder clb) {
				clb.with(
					RuntimeVisibleAnnotationsAttribute.of(
						Annotation.of(
							annotationClass("WasmTagExports"),
							List.of(element("value", AnnotationValue.ofArray(tags)))
						)
					)
				);
			}
		}));
	}

	private static void decorate(
		ModuleCompiler compiler,
		MethodBuilder mb,
		WasmExportRealization.OfInstanceMethod export
	) {
		var elements = new ArrayList<AnnotationElement>();
		elements.add(element("rename", AnnotationValue.ofString(export.exportName())));
		elements.add(element("allowEmptyName", AnnotationValue.ofBoolean(true)));
		String kind;
		var anns = new ArrayList<TypeAnnotation>();
		var target = TypeAnnotation.TargetInfo.ofMethodReturn();
		Signature returns = Signature.of(export.methodType().returnType());
		switch(export.externalType()) {
			case DefType ft -> {
				kind = "FUNC";
				elements.add(element("functionType", AnnotationValue.ofClass(compiler.getDefType(ft).classDesc())));
				var method = compiler.getMethodType(ft);
				mb.with(SignatureAttribute.of(method.methodSignature()));
				anns.addAll(method.methodTypeAnnotations());
			}
			case GlobalType gt -> {
				kind = "GLOBAL";
				if(gt.mutability() == Mut.Var && !compiler.getValType(gt.type()).type().isPrimitive()) {
					returns = Signature.ClassTypeSig.of(
						export.methodType().returnType(),
						Signature.TypeArg.of(Signature.ClassTypeSig.of(compiler.getValType(gt.type()).type()))
					);
					anns.addAll(
						typeUse(
							gt.type(),
							target,
							List.of(
								TypeAnnotation.TypePathComponent
									.of(TypeAnnotation.TypePathComponent.Kind.TYPE_ARGUMENT, 0)
							)
						)
					);
				}
				else
					anns.addAll(typeUse(gt.type(), target, List.of()));
				mb.with(SignatureAttribute.of(MethodSignature.of(returns)));
			}
			case TableType tt -> {
				kind = "TABLE";
				returns = Signature.ClassTypeSig.of(
					export.methodType().returnType(),
					Signature.TypeArg.of(Signature.ClassTypeSig.of(compiler.getValType(tt.elementType()).type()))
				);
				anns.addAll(
					typeUse(
						tt.elementType(),
						target,
						List.of(
							TypeAnnotation.TypePathComponent.of(TypeAnnotation.TypePathComponent.Kind.TYPE_ARGUMENT, 0)
						)
					)
				);
				anns.add(TypeAnnotation.of(target, List.of(), limits(tt.addrType(), tt.limits())));
				mb.with(SignatureAttribute.of(MethodSignature.of(returns)));
			}
			case TagType ignored -> throw new IllegalArgumentException("Unresolved export type");
			case MemType mt -> {
				kind = "MEMORY";
				anns.add(TypeAnnotation.of(target, List.of(), limits(mt.addrType(), mt.limits())));
			}
		}
		elements.add(element("type", AnnotationValue.ofEnum(annotationClass("WasmExport$ExportType"), kind)));
		mb.with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(annotationClass("WasmExport"), elements)));
		mb.with(RuntimeVisibleTypeAnnotationsAttribute.of(anns));
	}

	private static Annotation limits(dev.argon.jawawasm.runtime.AddrType address, Limits limits) {
		return Annotation.of(
			annotationClass("SizeLimits"),
			List.of(
				element("addressType", AnnotationValue.ofEnum(annotationClass("AddrType"), address.name())),
				element("min", AnnotationValue.ofLong(limits.min())),
				element("max", AnnotationValue.ofLong(limits.max() == null ? -1 : limits.max()))
			)
		);
	}
}
