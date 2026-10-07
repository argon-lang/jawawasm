package dev.argon.jawawasm.engine.aot;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import dev.argon.jawawasm.engine.compiler.ErasedResultType;
import dev.argon.jawawasm.engine.compiler.ModuleCompiler;
import dev.argon.jawawasm.engine.compiler.WasmExportRealization;
import dev.argon.jawawasm.engine.compiler.WasmModuleRealization;
import dev.argon.jawawasm.format.ModuleFormatException;
import dev.argon.jawawasm.format.types.*;
import dev.argon.jawawasm.runtime.*;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipFile;

import static java.lang.constant.ConstantDescs.*;

/**
 * Module loader that access types from classes.
 */
class AOTModuleLoader {
	/**
	 * Creates a module loader.
	 * @param compiler The compiler to be notified of loaded types.
	 */
	public AOTModuleLoader(ModuleCompiler compiler, ImmutableList<Path> classPath) {
		this.compiler = compiler;
		this.classPath = classPath;
	}

	private final ModuleCompiler compiler;
	private final ImmutableList<Path> classPath;


	private static final ClassDesc CD_WasmExport = ClassDesc.of("dev.argon.jawawasm.runtime.WasmExport");
	private static final ClassDesc CD_WasmTable = ClassDesc.of("dev.argon.jawawasm.runtime.WasmTable");
	private static final ClassDesc CD_SizeLimits = ClassDesc.of("dev.argon.jawawasm.runtime.SizeLimits");
	private static final ClassDesc CD_GlobalRef = ClassDesc.of("dev.argon.jawawasm.runtime.GlobalRef");
	private static final ClassDesc CD_GlobalI32 = ClassDesc.of("dev.argon.jawawasm.runtime.GlobalI32");
	private static final ClassDesc CD_GlobalI64 = ClassDesc.of("dev.argon.jawawasm.runtime.GlobalI64");
	private static final ClassDesc CD_GlobalF32 = ClassDesc.of("dev.argon.jawawasm.runtime.GlobalF32");
	private static final ClassDesc CD_GlobalF64 = ClassDesc.of("dev.argon.jawawasm.runtime.GlobalF64");
	private static final ClassDesc CD_WasmMemory = ClassDesc.of("dev.argon.jawawasm.runtime.WasmMemory");
	private static final ClassDesc CD_V128 = ClassDesc.of("dev.argon.jawawasm.runtime.V128");
	private static final ClassDesc CD_Nullable = ClassDesc.of("org.jspecify.annotations.Nullable");
	private static final ClassDesc CD_WasmFunction = ClassDesc.of("dev.argon.jawawasm.runtime.WasmFunction");
	private static final ClassDesc CD_NoneType = ClassDesc.of("dev.argon.jawawasm.runtime.NoneType");
	private static final ClassDesc CD_WebAssemblyException = ClassDesc.of("dev.argon.jawawasm.runtime.WebAssemblyException");
	private static final ClassDesc CD_ExternRef = ClassDesc.of("dev.argon.jawawasm.runtime.ExternRef");
	private static final ClassDesc CD_WasmEq = ClassDesc.of("dev.argon.jawawasm.runtime.WasmEq");
	private static final ClassDesc CD_I31 = ClassDesc.of("dev.argon.jawawasm.runtime.I31");
	private static final ClassDesc CD_WasmStruct = ClassDesc.of("dev.argon.jawawasm.runtime.WasmStruct");
	private static final ClassDesc CD_WasmArray = ClassDesc.of("dev.argon.jawawasm.runtime.WasmArray");

	private static Optional<Annotation> getAnnotation(List<Annotation> annotations, ClassDesc annotationType) {
		return annotations.stream()
			.filter(a -> ClassDesc.ofDescriptor(a.className().stringValue()).equals(annotationType))
			.findFirst();
	}

	private static Optional<AnnotationValue> getAnnotationValue(Annotation annotation, String name) {
		return annotation.elements().stream()
			.filter(e -> e.name().stringValue().equals(name))
			.map(AnnotationElement::value)
			.findFirst();
	}


	private ClassModel loadClassModel(ClassDesc classDesc) throws ModuleFormatException {
		ClassModel model;
		try {
			model = AOTClasspathLookup.loadClassModel(compiler.classFile(), classPath, classDesc);
		}
		catch(IOException e) {
			throw new ModuleFormatException("Failed to load class model", e);
		}

		if(model == null) {
			throw new ModuleFormatException("Class not found: " + classDesc.descriptorString());
		}

		return model;
	}


	private static Iterable<ClassDesc> nestedClasses(ClassModel model) {
		return model.findAttribute(Attributes.innerClasses())
			.map(attr -> attr.classes().stream()
				.filter(ic ->
					ic.outerClass()
						.map(o -> o.asInternalName()
							.equals(model.thisClass().asInternalName()))
						.orElse(false))
				.map(ic -> ic.innerClass().asSymbol())
				.toList()
			)
			.orElseGet(List::of);
	}

	private static ClassDesc getEnclosingClass(ClassModel model) throws ModuleFormatException {
		return model.findAttribute(Attributes.innerClasses())
			.stream()
			.flatMap(attr -> attr.classes().stream())
			.filter(ic -> ic.innerClass().asInternalName().equals(model.thisClass().asInternalName()))
			.flatMap(ic -> ic.outerClass().stream())
			.map(ClassEntry::asSymbol)
			.findFirst()
			.orElseThrow(() -> new ModuleFormatException("Class has no enclosing class"));
	}

	private static sealed interface TypeInfo {
		List<Annotation> annotations();
	}
	private static record KnownTypeInfo(
		ClassDesc classType,
		List<TypeInfo> typeArgs,
		List<Annotation> annotations
	) implements TypeInfo {}

	private static record TypeVarInfo(String name, List<Annotation> annotations) implements TypeInfo {}

	private static record ArrayTypeInfo(TypeInfo elementType, List<Annotation> annotations) implements TypeInfo {}

	private static record MethodTypeInfo(
		List<TypeInfo> paramTypes,
		List<String> typeParams,
		TypeInfo returnType
	) {}

	private static TypeInfo getTypeInfo(Signature sig, List<TypeAnnotation.TypePathComponent> path, List<TypeAnnotation> annotations) throws ModuleFormatException {
		var anns = annotations.stream()
			.filter(a -> a.targetPath().equals(path))
			.map(TypeAnnotation::annotation)
			.toList();

		if(sig instanceof Signature.BaseTypeSig base) {
			ClassDesc cd = ClassDesc.ofDescriptor(Character.toString(base.baseType()));
			return new KnownTypeInfo(cd, List.of(), anns);
		}
		else if(sig instanceof Signature.ClassTypeSig ct) {
			List<TypeInfo> typeArgs = new ArrayList<>();
			List<Signature.TypeArg> args = ct.typeArgs();
			for(int i = 0; i < args.size(); i++) {
				Signature.TypeArg typeArg = args.get(i);

				var nestedPath = new ArrayList<>(path);
				nestedPath.add(TypeAnnotation.TypePathComponent.of(TypeAnnotation.TypePathComponent.Kind.INNER_TYPE, i));

				if(typeArg instanceof Signature.TypeArg.Bounded bounded) {
					typeArgs.add(getTypeInfo(bounded.boundType(), nestedPath, annotations));
				}
				else {
					typeArgs.add(new KnownTypeInfo(CD_Object, List.of(), List.of()));
				}
			}

			return new KnownTypeInfo(
				ct.classDesc(),
				typeArgs,
				anns
			);
		}
		else if(sig instanceof Signature.TypeVarSig tv) {
			return new TypeVarInfo(tv.identifier(), anns);
		}
		else if(sig instanceof Signature.ArrayTypeSig at) {
			var nestedPath = new ArrayList<>(path);
			nestedPath.add(TypeAnnotation.TypePathComponent.of(TypeAnnotation.TypePathComponent.Kind.ARRAY, 0));

			var elementType = getTypeInfo(at.componentSignature(), nestedPath, annotations);

			return new ArrayTypeInfo(elementType, anns);
		}
		else {
			throw new ModuleFormatException("Unexpected signature type: " + sig);
		}
	}

	private static MethodTypeInfo getMethodTypeInfo(MethodModel method) throws ModuleFormatException {
		var signatureAttr = method.findAttribute(Attributes.signature()).orElse(null);
		var typeAnnAttr = method.findAttribute(Attributes.runtimeVisibleTypeAnnotations()).orElse(null);
		var typeAnns = typeAnnAttr != null ? typeAnnAttr.annotations() : List.<TypeAnnotation>of();

		List<Signature> paramSigs;
		List<String> typeParams;
		Signature returnSig;

		if (signatureAttr != null) {
			var sig = signatureAttr.asMethodSignature();
			paramSigs = sig.arguments();
			typeParams = sig.typeParameters().stream().map(Signature.TypeParam::identifier).toList();
			returnSig = sig.result();
		}
		else {
			var desc = method.methodTypeSymbol();
			paramSigs = desc.parameterList().stream().map(Signature::of).toList();
			typeParams = List.of();
			returnSig = Signature.of(desc.returnType());
		}

		var paramTypeInfos = ImmutableList.<TypeInfo>builder();
		for(int i = 0; i < paramSigs.size(); i++) {
			final int index = i;
			var annotations = typeAnns.stream()
				.filter(ta -> {
					if (ta.targetInfo() instanceof TypeAnnotation.FormalParameterTarget fpt) {
						return fpt.formalParameterIndex() == index;
					}
					return false;
				})
				.toList();

			paramTypeInfos.add(getTypeInfo(paramSigs.get(i), List.of(), annotations));
		}

		var returnAnnotations = typeAnns.stream()
			.filter(ta -> ta.targetInfo() instanceof TypeAnnotation.EmptyTarget)
			.toList();

		return new MethodTypeInfo(
			paramTypeInfos.build(),
			typeParams,
			getTypeInfo(returnSig, List.of(), returnAnnotations)
		);
	}



	/**
	 * Load a module from a class.
	 * @param moduleClassName The name of the class that defines the WebAssembly module.
	 * @return The realized module.
	 * @throws ModuleFormatException If the class defines an invalid module.
	 */
	public WasmModuleRealization loadModule(ClassDesc moduleClassName) throws ModuleFormatException {
		var moduleClass = loadClassModel(moduleClassName);

		var exports = ImmutableList.<WasmExportRealization>builder();
		for(var method : moduleClass.methods()) {
			var export = loadExport(method);
			if(export != null) {
				exports.add(export);
			}
		}
		for(var innerClass : nestedClasses(moduleClass)) {
			var export = loadExport(innerClass);
			if(export != null) {
				exports.add(export);
			}
		}

		return new WasmModuleRealization(
			moduleClass.thisClass().asSymbol(),
			exports.build()
		);
	}

	private ResultType loadResultType(TypeInfo t) throws ModuleFormatException {
		ClassDesc tClass;
		List<TypeInfo> typeArgs;

		if(t instanceof KnownTypeInfo kt) {
			tClass = kt.classType();
			typeArgs = kt.typeArgs();
		}
		else {
			throw new ModuleFormatException("Unexpected result type: " + t);
		}

		var erasedResultType = loadResultClass(tClass);

		var elementTypes = ImmutableList.<ValType>builder();
		int typeArgIndex = 0;
		for(var elementKind : erasedResultType.types()) {
			var elementType = switch(elementKind) {
				case INT -> NumType.I32;
				case LONG -> NumType.I64;
				case FLOAT -> NumType.F32;
				case DOUBLE -> NumType.F64;
				case REFERENCE -> {
					if(typeArgIndex >= typeArgs.size()) {
						throw new ModuleFormatException("Result type argument mismatch. Not enough arguments: " + t);
					}

					var elemType = loadValType(typeArgs.get(typeArgIndex));
					++typeArgIndex;
					yield elemType;
				}
				default -> throw new ModuleFormatException("Unexpected result type element: " + elementKind);
			};

			elementTypes.add(elementType);
		}

		if(typeArgIndex != typeArgs.size()) {
			throw new ModuleFormatException("Result type argument mismatch. Too many arguments: " + t);
		}

		return new ResultType(elementTypes.build());
	}

	/**
	 * Loads a class as a result type.
	 * @param tDesc The result class descriptor.
	 * @return The erased result type.
	 * @throws ModuleFormatException if the class is not a valid result type.
	 */
	public ErasedResultType loadResultClass(ClassDesc tDesc) throws ModuleFormatException {
		if(!tDesc.packageName().equals("dev.argon.jawawasm.runtime")) {
			throw new ModuleFormatException("Result types must be defined in dev.argon.jawawasm.runtime, Found: " + tDesc.descriptorString());
		}

		var tModel = loadClassModel(tDesc);

		var endResultClass = tModel.findAttribute(Attributes.innerClasses())
			.stream()
			.flatMap(attr -> attr.classes().stream())
			.filter(ic ->
				ic.outerClass().map(oc -> oc.asInternalName().equals(tModel.thisClass().asInternalName())).orElse(false) &&
					ic.innerClass().asSymbol().equals(tDesc.nested("EndResult"))
			)
			.findAny()
			.orElse(null);

		if(endResultClass == null) {
			throw new ModuleFormatException("Invalid result type. Nested class EndResult is missing");
		}



		var ofMethods = tModel.methods().stream()
			.filter(m ->
				m.methodName().stringValue().equals("of") &&
					m.flags().has(AccessFlag.PUBLIC) &&
					m.flags().has(AccessFlag.STATIC)
			)
			.toList();
		if(ofMethods.size() != 1) {
			throw new ModuleFormatException("Invalid result type. It must have exactly one static public method named of.");
		}

		var ofMethod = ofMethods.getFirst();

		if(!ofMethod.methodTypeSymbol().returnType().equals(tDesc)) {
			throw new ModuleFormatException("Invalid result type. \"of\" method must return the result type. Result type: " + tDesc + ", Return type: " + ofMethod.methodTypeSymbol().returnType());
		}

		var methodType = getMethodTypeInfo(ofMethod);
		var paramTypes = methodType.paramTypes();
		var typeParams = methodType.typeParams();


		int typeParamIndex = 0;

		var resTypes = ImmutableList.<TypeKind>builder();
		for(int i = 0; i < paramTypes.size(); i++) {
			var ctorParamType = paramTypes.get(i);
			TypeKind typeKind;
			switch(ctorParamType) {
				case KnownTypeInfo kt -> {
					typeKind = switch(kt.classType().descriptorString().charAt(0)) {
						case 'I' -> TypeKind.INT;
						case 'J' -> TypeKind.LONG;
						case 'F' -> TypeKind.FLOAT;
						case 'D' -> TypeKind.DOUBLE;
						default ->
							throw new ModuleFormatException("Unexpected base type in result type: " + kt.classType().descriptorString());
					};
				}
				case TypeVarInfo tv -> {
					if(typeParamIndex >= typeParams.size()) {
						throw new ModuleFormatException("Not enough type parameters on result type: " + tDesc.displayName());
					}

					if(!tv.name().equals(typeParams.get(typeParamIndex))) {
						throw new ModuleFormatException("Type parameters must match one to one with usages in 'of' method.");
					}

					typeKind = TypeKind.REFERENCE;
					++typeParamIndex;
				}
				default ->
					throw new ModuleFormatException("Unexpected type in result type signature. Expected base type or type variable");
			}

			resTypes.add(typeKind);
		}

		if(typeParamIndex != typeParams.size()) {
			throw new ModuleFormatException("Too many type parameters for result type: " + tDesc.displayName());
		}

		var resultType = new ErasedResultType(resTypes.build());

		compiler.registerResultType(resultType, tDesc);

		return resultType;
	}

	private @Nullable WasmExportRealization loadExport(MethodModel method) throws ModuleFormatException {
		if(!method.flags().has(AccessFlag.PUBLIC)) {
			return null;
		}

		if(method.flags().has(AccessFlag.STATIC)) {
			return null;
		}

		var annotationsAttr = method.findAttribute(Attributes.runtimeVisibleAnnotations()).orElse(null);
		var annotations = annotationsAttr != null ? annotationsAttr.annotations() : List.<Annotation>of();

		var exportAnn = getAnnotation(annotations, CD_WasmExport).orElse(null);
		if(exportAnn == null) {
			return null;
		}

		String rename = getAnnotationValue(exportAnn, "rename").map(av -> ((AnnotationValue.OfString)av).stringValue()).orElse("");
		boolean allowEmptyName = getAnnotationValue(exportAnn, "allowEmptyName").map(av -> ((AnnotationValue.OfBoolean)av).booleanValue()).orElse(false);

		String name;
		if(allowEmptyName || !rename.isEmpty()) {
			name = rename;
		}
		else {
			name = method.methodName().stringValue();
		}

		var type = method.methodTypeSymbol();

		var exportTypeStr = getAnnotationValue(exportAnn, "type").map(av -> ((AnnotationValue.OfEnum)av).constantName().stringValue()).orElse("FUNC");
		var exportType = WasmExport.ExportType.valueOf(exportTypeStr);

		var externalType = loadExternalType(exportType, method);

		return new WasmExportRealization.OfInstanceMethod(name, method.methodName().stringValue(), type, externalType);
	}

	private @Nullable WasmExportRealization loadExport(ClassDesc innerClassDesc) throws ModuleFormatException {
		var innerClassModel = loadClassModel(innerClassDesc);

		if(!innerClassModel.flags().has(AccessFlag.PUBLIC)) {
			return null;
		}

		if(!innerClassModel.flags().has(AccessFlag.STATIC)) {
			return null;
		}

		if(!innerClassModel.superclass().map(sc -> sc.asSymbol().equals(CD_WebAssemblyException)).orElse(false)) {
			return null;
		}

		var constructors = innerClassModel.methods().stream()
			.filter(m -> m.methodName().stringValue().equals("<init>") && m.flags().has(AccessFlag.PUBLIC))
			.toList();

		if(constructors.size() != 1) {
			throw new ModuleFormatException("A WebAssembly exception type is expected to have exactly one public constructor");
		}

		var constructor = constructors.getFirst();


		String name;

		var annotationsAttr = innerClassModel.findAttribute(Attributes.runtimeVisibleAnnotations()).orElse(null);
		var annotations = annotationsAttr != null ? annotationsAttr.annotations() : List.<Annotation>of();

		var exportAnn = getAnnotation(annotations, CD_WasmExport).orElse(null);
		if(exportAnn != null) {
			String rename = getAnnotationValue(exportAnn, "rename").map(av -> ((AnnotationValue.OfString)av).stringValue()).orElse("");
			boolean allowEmptyName = getAnnotationValue(exportAnn, "allowEmptyName").map(av -> ((AnnotationValue.OfBoolean)av).booleanValue()).orElse(false);

			if(allowEmptyName || !rename.isEmpty()) {
				name = rename;
			}
			else {
				name = innerClassDesc.displayName();
			}
		}
		else {
			name = innerClassDesc.displayName();
		}


		var methodType = getMethodTypeInfo(constructor);
		var paramTypes = ImmutableList.<ValType>builder();
		for(var param : methodType.paramTypes()) {
			paramTypes.add(loadValType(param));
		}

		var realizedParamTypes = constructor.methodTypeSymbol().parameterList();

		var funcType = new FuncType(new ResultType(paramTypes.build()), new ResultType(ImmutableList.of()));

		var parentClass = getEnclosingClass(innerClassModel);

		return new WasmExportRealization.OfInnerClass(
			name,
			innerClassDesc,
			InnerClassInfo.of(
				innerClassDesc,
				Optional.of(parentClass),
				Optional.of(innerClassDesc.displayName()),
				innerClassModel.flags().flagsMask()
			),
			MethodTypeDesc.of(CD_void, realizedParamTypes),
			funcType,
			new DefType(
				new RecursiveType(ImmutableList.of(
					new SubType(true, ImmutableList.of(), funcType)
				)),
				0
			)
		);
	}

	private ExternalType loadExternalType(WasmExport.ExportType exportType, MethodModel method) throws ModuleFormatException {
		var methodType = getMethodTypeInfo(method);
		var returnType = methodType.returnType();

		return switch(exportType) {
			case FUNC -> loadExternalFunctionType(method);
			case TABLE -> loadExternalTableType(returnType);
			case GLOBAL -> loadExternalGlobalType(returnType);
			case MEMORY -> loadExternalMemoryType(returnType);
		};
	}

	private DefType loadExternalFunctionType(MethodModel method) throws ModuleFormatException {
		var paramTypes = ImmutableList.<ValType>builder();
		var methodType = getMethodTypeInfo(method);
		for(var param : methodType.paramTypes()) {
			paramTypes.add(loadValType(param));
		}

		var resultType = loadResultType(methodType.returnType());

		var funcType = new FuncType(new ResultType(paramTypes.build()), resultType);

		return new DefType(
			new RecursiveType(ImmutableList.of(
				new SubType(true, ImmutableList.of(), funcType)
			)),
			0
		);
	}

	private TableType loadExternalTableType(TypeInfo returnType) throws ModuleFormatException {
		if(!(returnType instanceof KnownTypeInfo kt) || !kt.classType().equals(CD_WasmTable)) {
			throw new ModuleFormatException("A table export must return a WasmTable. Actual: " + returnType);
		}

		var typeArgs = kt.typeArgs();

		if(typeArgs.size() != 1) {
			throw new ModuleFormatException("Incorrect type arguments for WasmTable");
		}

		var sizeLimitsAnn = getAnnotation(returnType.annotations(), CD_SizeLimits).orElse(null);
		if(sizeLimitsAnn == null) {
			throw new ModuleFormatException("Missing SizeLimits for table type");
		}

		var addressTypeStr = getAnnotationValue(sizeLimitsAnn, "addressType").map(av -> ((AnnotationValue.OfEnum)av).constantName().stringValue()).orElseThrow(() -> new ModuleFormatException("Missing addressType in SizeLimits"));
		var addressType = AddrType.valueOf(addressTypeStr);
		long min = getAnnotationValue(sizeLimitsAnn, "min").map(av -> ((AnnotationValue.OfLong)av).longValue()).orElseThrow(() -> new ModuleFormatException("Missing min in SizeLimits"));
		long max = getAnnotationValue(sizeLimitsAnn, "max").map(av -> ((AnnotationValue.OfLong)av).longValue()).orElse(-1L);


		var typeArg = typeArgs.getFirst();
		var elementType = loadRefType(typeArg);
		return new TableType(addressType, new Limits(min, max == -1L ? null : max), elementType);
	}

	private GlobalType loadExternalGlobalType(TypeInfo returnType) throws ModuleFormatException {
		if(returnType instanceof KnownTypeInfo kt) {
			var cd = kt.classType();
			if(cd.equals(CD_GlobalRef)) {
				var typeArgs = kt.typeArgs();
				if(typeArgs.size() != 1) {
					throw new ModuleFormatException("Incorrect type arguments for GlobalRef");
				}

				var typeArg = typeArgs.getFirst();
				if(!(typeArg instanceof KnownTypeInfo ktArg)) {
					throw new ModuleFormatException("Invalid type argument for GlobalRef");
				}

				var elementType = loadValType(ktArg);
				return new GlobalType(Mut.Var, elementType);
			}
			else if(cd.equals(CD_GlobalI32)) {
				return new GlobalType(Mut.Var, NumType.I32);
			}
			else if(cd.equals(CD_GlobalI64)) {
				return new GlobalType(Mut.Var, NumType.I64);
			}
			else if(cd.equals(CD_GlobalF32)) {
				return new GlobalType(Mut.Var, NumType.F32);
			}
			else if(cd.equals(CD_GlobalF64)) {
				return new GlobalType(Mut.Var, NumType.F64);
			}
		}

		var t = loadValType(returnType);
		return new GlobalType(Mut.Const, t);
	}

	private MemType loadExternalMemoryType(TypeInfo returnType) throws ModuleFormatException {
		if(!(returnType instanceof KnownTypeInfo kt) || !kt.classType().equals(CD_WasmMemory)) {
			throw new ModuleFormatException("A memory export must return a WasmMemory. Actual: " + returnType);
		}

		var sizeLimitsAnn = getAnnotation(returnType.annotations(), CD_SizeLimits).orElse(null);
		if(sizeLimitsAnn == null) {
			throw new ModuleFormatException("Missing SizeLimits for memory type");
		}

		var addressTypeStr = getAnnotationValue(sizeLimitsAnn, "addressType").map(av -> ((AnnotationValue.OfEnum)av).constantName().stringValue()).orElseThrow(() -> new ModuleFormatException("Missing addressType in SizeLimits"));
		var addressType = AddrType.valueOf(addressTypeStr);
		long min = getAnnotationValue(sizeLimitsAnn, "min").map(av -> ((AnnotationValue.OfLong)av).longValue()).orElseThrow(() -> new ModuleFormatException("Missing min in SizeLimits"));
		long max = getAnnotationValue(sizeLimitsAnn, "max").map(av -> ((AnnotationValue.OfLong)av).longValue()).orElse(-1L);

		return new MemType(addressType, new Limits(min, max == -1L ? null : max));
	}



	private ValType loadValType(TypeInfo t) throws ModuleFormatException {
		if(t instanceof KnownTypeInfo kt) {
			if(kt.classType().isPrimitive()) {
				return switch(kt.classType().descriptorString()) {
					case "I" -> NumType.I32;
					case "J" -> NumType.I64;
					case "F" -> NumType.F32;
					case "D" -> NumType.F64;
					default -> throw new ModuleFormatException("Unexpected primitive type: " + kt.classType().descriptorString());
				};
			}
			else if(kt.classType().equals(CD_V128)) {
				return VecType.V128;
			}
		}

		return loadRefType(t);
	}

	private RefType loadRefType(TypeInfo t) throws ModuleFormatException {
		boolean isNullable = getAnnotation(t.annotations(), CD_Nullable).isPresent();

		var heapType = loadHeapType(t);
		return new RefType(isNullable, heapType);
	}

	private HeapType loadHeapType(TypeInfo t) throws ModuleFormatException {
		if(!(t instanceof KnownTypeInfo kt)) {
			throw new ModuleFormatException("Invalid heap type: " + t);
		}

		var cd = kt.classType();

		if(cd.equals(CD_WasmFunction)) {
			if(getAnnotation(t.annotations(), CD_NoneType).isPresent()) {
				return HeapType.AbstractHeapType.NOFUNC;
			}
			else {
				return HeapType.AbstractHeapType.FUNC;
			}
		}
		else if(cd.equals(CD_WebAssemblyException)) {
			if(getAnnotation(t.annotations(), CD_NoneType).isPresent()) {
				return HeapType.AbstractHeapType.NOEXN;
			}
			else {
				return HeapType.AbstractHeapType.EXN;
			}
		}
		else if(cd.equals(CD_Object)) {
			if(getAnnotation(t.annotations(), CD_ExternRef).isPresent()) {
				if(getAnnotation(t.annotations(), CD_NoneType).isPresent()) {
					return HeapType.AbstractHeapType.NOEXTERN;
				}
				else {
					return HeapType.AbstractHeapType.EXTERN;
				}
			}
			else {
				if(getAnnotation(t.annotations(), CD_NoneType).isPresent()) {
					return HeapType.AbstractHeapType.NONE;
				}
				else {
					return HeapType.AbstractHeapType.ANY;
				}
			}
		}
		else if(cd.equals(CD_WasmEq)) {
			return HeapType.AbstractHeapType.EQ;
		}
		else if(cd.equals(CD_I31)) {
			return HeapType.AbstractHeapType.I31;
		}
		else if(cd.equals(CD_WasmStruct)) {
			return HeapType.AbstractHeapType.STRUCT;
		}
		else if(cd.equals(CD_WasmArray)) {
			return HeapType.AbstractHeapType.ARRAY;
		}
		else {
			throw new RuntimeException("Not implemented");
		}
	}

}
