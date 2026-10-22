package dev.argon.jawawasm.engine.compiler;

import dev.argon.jawawasm.format.types.*;
import java.lang.classfile.*;
import java.lang.constant.ClassDesc;
import java.util.*;

final class TypeAnnotations {
	private TypeAnnotations() {}

	static ClassDesc annotationClass(String name) {
		return ClassDesc.of("dev.argon.jawawasm.runtime." + name);
	}

	static AnnotationElement element(String name, AnnotationValue value) {
		return AnnotationElement.of(name, value);
	}

	static AnnotationValue classes(List<ClassDesc> classes) {
		return AnnotationValue
			.ofArray(classes.stream().map(AnnotationValue::ofClass).map(v -> (AnnotationValue)v).toList());
	}

	static List<Annotation> declaration(ModuleCompiler compiler, DefType type) {
		var sub = type.recursiveType().subtypes().get(type.index());
		var group = new ArrayList<ClassDesc>();
		for(int i = 0; i < type.recursiveType().subtypes().size(); i++) {
			group.add(compiler.getDefType(new DefType(type.recursiveType(), i)).classDesc());
		}
		var subtype = Annotation.of(
			annotationClass("WasmSubtype"),
			List.of(
				element(
					"supertypes",
					classes(sub.superTypes().stream().map(t -> heapClass(compiler, type, t)).toList())
				),
				element("recursiveGroup", classes(group)),
				element("groupIndex", AnnotationValue.ofInt(type.index()))
			)
		);
		Annotation concrete = switch(sub.compositeType()) {
			case StructType st -> Annotation.of(annotationClass("WasmStructType"));
			case ArrayType at -> Annotation.of(annotationClass("WasmArrayType"));
			case FuncType ft -> Annotation.of(
				annotationClass("WasmFuncType"),
				List.of(element("isFinal", AnnotationValue.ofBoolean(sub.isFinal())))
			);
		};
		return List.of(subtype, concrete);
	}

	private static ClassDesc heapClass(ModuleCompiler compiler, DefType context, HeapType heap) {
		if(heap instanceof RecTypeIdx idx)
			return compiler.getDefType(new DefType(context.recursiveType(), idx.index())).classDesc();
		return compiler.getHeapType(heap);
	}

}
