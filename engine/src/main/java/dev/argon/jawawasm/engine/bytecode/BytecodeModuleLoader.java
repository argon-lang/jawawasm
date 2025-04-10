package dev.argon.jawawasm.engine.bytecode;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import dev.argon.jawawasm.engine.compiler.ErasedResultType;
import dev.argon.jawawasm.engine.compiler.ModuleCompiler;
import dev.argon.jawawasm.engine.compiler.WasmExportRealization;
import dev.argon.jawawasm.engine.compiler.WasmModuleRealization;
import dev.argon.jawawasm.format.ModuleFormatException;
import dev.argon.jawawasm.format.types.*;
import dev.argon.jawawasm.runtime.*;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.ClassDesc;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static dev.argon.jawawasm.engine.internal.Constants.*;
import static java.lang.constant.ConstantDescs.*;

/**
 * Module loader that uses class files.
 */
public class BytecodeModuleLoader  {
	/**
	 * Creates a module loader.
	 * @param compiler The compiler to be notified of loaded types.
	 * @param modulePath The paths of modules to load from.
	 * @param release The maximum supported JDK release version.
	 * @throws IOException if the module path or JDK cannot be cloaded.
	 */
	public BytecodeModuleLoader(ModuleCompiler compiler, List<Path> modulePath, int release) throws IOException {
		this.compiler = compiler;

		var loaders = ImmutableList.<LibraryLoader>builder();
		try {
			var ctSymPath = Path.of(System.getProperty("java.home"), "lib/ct.sym");
			loaders.add(new CTSymLibraryLoader(ctSymPath, release));

			for(var p : modulePath) {
				if(Files.isDirectory(p)) {
					loaders.add(new DirectoryLibraryLoader(p));
				}
				else {
					loaders.add(new JarLibraryLoader(p, release));
				}
			}

			loader = new CompoundLibraryLoader(loaders.build());
			loaders = null;
		}
		finally {
			if(loaders != null) {
				for(var loader : loaders.build()) {
					loader.close();
				}
			}
		}

	}

	private final ModuleCompiler compiler;
	private final LibraryLoader loader;

	/**
	 * Scans classes for types to use.
	 * @throws ModuleFormatException if an invalid WebAssembly type is found
	 */
	public void scanClasses() throws ModuleFormatException {
		try {
			try(var classes = loader.allClasses()) {
				var iter = classes.iterator();
				while(iter.hasNext()) {
					scanClass(iter.next());
				}
			}
		}
		catch(UncheckedIOException e) {
			throw uncheckedIOToModuleError(e);
		}
	}

	private void scanClass(JavaClass javaClass) throws ModuleFormatException {
		ClassModel classModel;
		try {
			byte[] classBytes;
			try(var is = javaClass.read()) {
				classBytes = ByteStreams.toByteArray(is);
			}

			classModel = compiler.classFile().parse(classBytes);
		} catch(IOException e) {
			throw ioToModuleError(e);
		}

		if(!classModel.flags().has(AccessFlag.PUBLIC)) {
			return;
		}

		if(!isTopLevelClass(classModel)) {
			return;
		}

		var superClass = classModel.superclass()
			.map(ClassEntry::asInternalName)
			.map(ClassDesc::ofInternalName)
			.orElse(null);
		if(superClass == null) {
			return;
		}

		if(!classModel.flags().has(AccessFlag.ABSTRACT) && implementsInterface(javaClass.descriptor(), wasmResultClass)) {
			loadResultClass(classModel);
		}
	}

	/**
	 * Load a module from a class.
	 * @param moduleClass The class that defines the WebAssembly module.
	 * @return The realized module.
	 * @throws ModuleFormatException If the class defines an invalid module.
	 */
	public WasmModuleRealization loadModule(ClassModel moduleClass) throws ModuleFormatException {
		var exports = ImmutableList.<WasmExportRealization>builder();
		for(var method : moduleClass.methods()) {
			var export = loadExport(method);
			if(export != null) {
				exports.add(export);
			}
		}
		for(var innerClass :
			moduleClass.findAttribute(Attributes.innerClasses())
				.map(InnerClassesAttribute::classes)
				.orElse(List.of())
		) {
			if(!innerClass.outerClass().map(oc -> oc.asSymbol().equals(moduleClass.thisClass().asSymbol())).orElse(false)) {
				continue;
			}

			if(!innerClass.has(AccessFlag.PUBLIC) || !innerClass.has(AccessFlag.STATIC)) {
				continue;
			}

			var innerClassModel = loadClass(innerClass.innerClass().asSymbol());
			if(innerClassModel == null) {
				continue;
			}

			var export = loadExport(innerClassModel, innerClass);
			if(export != null) {
				exports.add(export);
			}
		}

		return new WasmModuleRealization(
			moduleClass.thisClass().asSymbol(),
			exports.build()
		);
	}

	ResultType loadResultType(Signature t, List<TypeAnns> typeArgAnns) throws ModuleFormatException {
		ClassDesc tClass;
		List<Signature.TypeArg> typeArgs;

		if(t instanceof Signature.ClassTypeSig ct) {
			tClass = ct.classDesc();
			typeArgs = ct.typeArgs();
		}
		else {
			throw new ModuleFormatException("Unexpected result type: " + t);
		}

		var tModel = loadClass(tClass);
		if(tModel == null) {
			throw new ModuleFormatException("Could not load result class: " + t);
		}


		var erasedResultType = loadResultClass(tModel);

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

					Signature elemTypeSig;
					switch(typeArgs.get(typeArgIndex)) {
						case Signature.TypeArg.Bounded bounded -> {
							if(bounded.wildcardIndicator() != Signature.TypeArg.Bounded.WildcardIndicator.EXTENDS) {
								throw new ModuleFormatException("Result type argument must have an extends type bound");
							}

							elemTypeSig = bounded.boundType();
						}
						case Signature.TypeArg.Unbounded _ -> {
							throw new ModuleFormatException("Result type argument must have an explicit type bound");
						}
					}

					var elemType = loadValType(elemTypeSig, typeArgAnns.get(typeArgIndex));
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
	 * @param t The result class.
	 * @return The erased result type.
	 * @throws ModuleFormatException if the class is not a valid result type.
	 */
	public ErasedResultType loadResultClass(ClassModel t) throws ModuleFormatException {
		if(!hasInnerClass(t, "EndResult")) {
			throw new ModuleFormatException("Invalid result type. Nested class EndResult is missing");
		}

		var ofMethods = t.methods().stream()
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

		if(ofMethod.methodTypeSymbol().returnType().equals(t.thisClass().asSymbol())) {
			throw new ModuleFormatException("Invalid result type. \"of\" method must return the result type. Result type: " + t + ", Return type: " + ofMethod.methodTypeSymbol().returnType());
		}

		var typeParams = getClassTypeParameters(t);
		int typeParamIndex = 0;

		var resTypes = ImmutableList.<TypeKind>builder();
		for(var ctorParamType : getMethodSigParameters(ofMethod)) {
			TypeKind typeKind;
			if(ctorParamType.equals(Signature.of(CD_int))) {
				typeKind = TypeKind.INT;
			}
			else if(ctorParamType.equals(Signature.of(CD_long))) {
				typeKind = TypeKind.LONG;
			}
			else if(ctorParamType.equals(Signature.of(CD_float))) {
				typeKind = TypeKind.FLOAT;
			}
			else if(ctorParamType.equals(Signature.of(CD_double))) {
				typeKind = TypeKind.DOUBLE;
			}
			else if(ctorParamType instanceof Signature.TypeVarSig tv) {
				if(typeParamIndex >= typeParams.size()) {
					throw new ModuleFormatException("Not enough type parameters on result type: " + t.thisClass().asInternalName());
				}

				if(!tv.identifier().equals(typeParams.get(typeParamIndex).identifier())) {
					throw new ModuleFormatException("Type parameters must match one to one with usages in 'of' method.");
				}

				typeKind = TypeKind.REFERENCE;
				++typeParamIndex;
			}
			else {
				throw new ModuleFormatException("Unexpected type in result type. Expected int, long, float, double, or type variable");
			}

			resTypes.add(typeKind);
		}

		if(typeParamIndex != typeParams.size()) {
			throw new ModuleFormatException("Too many type parameters for result type: " + t.thisClass());
		}

		var resultType = new ErasedResultType(resTypes.build());

		var resTypeDesc = t.thisClass().asSymbol();

		compiler.registerResultType(resultType, resTypeDesc);

		return resultType;
	}

	private @Nullable WasmExportRealization loadExport(MethodModel method) throws ModuleFormatException {
		if(!method.flags().has(AccessFlag.PUBLIC)) {
			return null;
		}

		if(method.flags().has(AccessFlag.STATIC)) {
			return null;
		}

		var exportAnnEnc = method.findAttribute(Attributes.runtimeVisibleAnnotations())
			.flatMap(s -> s
				.annotations()
				.stream()
				.filter(ann -> ann.classSymbol().equals(wasmExportAnn))
				.findFirst()
			)
			.orElse(null);
		if(exportAnnEnc == null) {
			return null;
		}

		var exportAnn = decodeWasmExportAnn(exportAnnEnc);

		var externalType = loadExternalType(exportAnn.type(), method);

		String name;
		if(exportAnn.allowEmptyName() || !exportAnn.rename().isEmpty()) {
			name = exportAnn.rename();
		}
		else {
			name = method.methodName().stringValue();
		}

		return new WasmExportRealization.OfInstanceMethod(name, method.methodName().stringValue(), method.methodTypeSymbol(), externalType);
	}

	private static WasmExport decodeWasmExportAnn(Annotation exportAnn) throws ModuleFormatException {
		String annRename = "";
		boolean allowEmptyName = false;
		WasmExport.ExportType exportType = null;
		for(var element : exportAnn.elements()) {
			switch(element.name().stringValue()) {
				case "allowEmptyName":
					allowEmptyName = switch(element.value()) {
						case AnnotationValue.OfBoolean annBoolean -> annBoolean.booleanValue();
						default -> throw new ModuleFormatException("Expected boolean for allowEmptyName. Actual: " + element.value());
					};
					break;

				case "rename":
					annRename = switch(element.value()) {
						case AnnotationValue.OfString annStr -> annStr.stringValue();
						default -> throw new ModuleFormatException("Expected string for rename. Actual: " + element.value());
					};
					break;

				case "type":
					exportType = switch(element.value()) {
						case AnnotationValue.OfEnum annEnum -> WasmExport.ExportType.valueOf(annEnum.constantName().stringValue());
						default -> throw new ModuleFormatException("Expected enum for type. Actual: " + element.value());
					};
					break;

				default:
					throw new ModuleFormatException("Unexpected annotation element for export: " + element);
			}
		}

		if(exportType == null) {
			throw new ModuleFormatException("Export type not specified");
		}

		var annRename2 = annRename;
		var allowEmptyName2 = allowEmptyName;
		var exportType2 = exportType;

		return new WasmExportImpl(annRename2, allowEmptyName2, exportType2);
	}

	private static SizeLimits decodeReturnTypeSizeLimits(MethodModel method) throws ModuleFormatException {
		var sizeLimitAnn = method.findAttribute(Attributes.runtimeVisibleTypeAnnotations())
			.flatMap(anns -> anns
				.annotations()
				.stream()
				.filter(ann ->
					ann.targetInfo().targetType() == TypeAnnotation.TargetType.METHOD_RETURN &&
						ann.targetPath().isEmpty() &&
						ann.annotation().classSymbol().equals(sizeLimitsAnn)
				)
				.findFirst()
			)
			.map(TypeAnnotation::annotation)
			.orElse(null);

		if(sizeLimitAnn == null) {
			throw new ModuleFormatException("Missing size limits");
		}


		AddrType addressType = null;
		Long min = null;
		Long max = null;
		for(var element : sizeLimitAnn.elements()) {
			switch(element.name().stringValue()) {
				case "addressType":
					addressType = switch(element.value()) {
						case AnnotationValue.OfEnum annEnum -> AddrType.valueOf(annEnum.constantName().stringValue());
						default -> throw new ModuleFormatException("Expected enum for addressType. Actual: " + element.value());
					};
					break;

				case "min":
					min = switch(element.value()) {
						case AnnotationValue.OfLong annLong -> annLong.longValue();
						default -> throw new ModuleFormatException("Expected boolean for min. Actual: " + element.value());
					};
					break;

				case "max":
					max = switch(element.value()) {
						case AnnotationValue.OfLong annLong -> annLong.longValue();
						default -> throw new ModuleFormatException("Expected string for max. Actual: " + element.value());
					};
					break;


				default:
					throw new ModuleFormatException("Unexpected annotation element for export: " + element);
			}
		}

		if(addressType == null) {
			throw new ModuleFormatException("Address type not specified");
		}

		if(min == null) {
			throw new ModuleFormatException("Min size not specified");
		}



		AddrType addressType2 = addressType;
		long min2 = min;
		long max2 = max == null ? -1 : max;

		return new SizeLimitsImpl(addressType2, min2, max2);
	}

	private @Nullable WasmExportRealization loadExport(ClassModel innerClass, InnerClassInfo innerClassInfo) throws ModuleFormatException {
		if(!innerClass.flags().has(AccessFlag.PUBLIC)) {
			return null;
		}

		if(!innerClass.superclass().map(sc -> sc.asSymbol().equals(webAssemblyExceptionClass)).orElse(false)) {
			return null;
		}

		var constructors = innerClass.methods()
			.stream()
			.filter(c ->
				c.flags().has(AccessFlag.PUBLIC) &&
					c.methodName().stringValue().equals("<init>")
			)
			.toList();

		if(constructors.size() != 1) {
			throw new ModuleFormatException("A WebAssembly exception type is expected to have exactly one public constructor");
		}

		var constructor = constructors.getFirst();


		String name;


		var exportAnnEnc = innerClass.findAttribute(Attributes.runtimeVisibleAnnotations())
			.flatMap(s -> s
				.annotations()
				.stream()
				.filter(ann -> ann.classSymbol().equals(wasmExportAnn))
				.findFirst()
			)
			.orElse(null);
		if(exportAnnEnc == null) {
			return null;
		}

		var exportAnn = decodeWasmExportAnn(exportAnnEnc);

		if(exportAnn.allowEmptyName() || !exportAnn.rename().isEmpty()) {
			name = exportAnn.rename();
		}
		else {
			var innerName = innerClassInfo.innerName().map(Utf8Entry::stringValue).orElse(null);
			if(innerName == null) {
				throw new ModuleFormatException("Missing inner name for exception export");
			}

			name = innerName;
		}

		var paramSigs = getMethodSigParameters(constructor);

		var paramTypes = ImmutableList.<ValType>builder();
		for(int i = 0; i < paramSigs.size(); ++i) {
			paramTypes.add(loadValType(paramSigs.get(i), getParameterTypeAnns(constructor, i)));
		}

		var funcType = new FuncType(new ResultType(paramTypes.build()), new ResultType(ImmutableList.of()));

		return new WasmExportRealization.OfInnerClass(
			name,
			innerClass.thisClass().asSymbol(),
			innerClassInfo,
			constructor.methodTypeSymbol(),
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
		return switch(exportType) {
			case FUNC -> loadExternalFunctionType(method);
			case TABLE -> loadExternalTableType(method);
			case GLOBAL -> loadExternalGlobalType(method);
			case MEMORY -> loadExternalMemoryType(method);
		};
	}

	private DefType loadExternalFunctionType(MethodModel method) throws ModuleFormatException {
		var paramSigs = getMethodSigParameters(method);

		var paramTypes = ImmutableList.<ValType>builder();
		for(int i = 0; i < paramSigs.size(); ++i) {
			paramTypes.add(loadValType(paramSigs.get(i), getParameterTypeAnns(method, i)));
		}

		var resultSig = getMethodSigReturnType(method);


		List<TypeAnns> resultTypeArgAnns = new ArrayList<>();
		if(resultSig instanceof Signature.ClassTypeSig ctSig) {
			for(int i = 0; i < ctSig.typeArgs().size(); ++i) {
				resultTypeArgAnns.add(getReturnTypeArgAnns(method, i));
			}
		}

		var resultType = loadResultType(getMethodSigReturnType(method), resultTypeArgAnns);

		var funcType = new FuncType(new ResultType(paramTypes.build()), resultType);

		return new DefType(
			new RecursiveType(ImmutableList.of(
				new SubType(true, ImmutableList.of(), funcType)
			)),
			0
		);
	}

	private TableType loadExternalTableType(MethodModel method) throws ModuleFormatException {
		Signature returnType = getMethodSigReturnType(method);

		if(!(returnType instanceof Signature.ClassTypeSig tableType) || !tableType.classDesc().equals(wasmTableClass)) {
			throw new ModuleFormatException("A table export must return a WasmTable. Actual: " + returnType);
		}

		var typeArgs = tableType.typeArgs();

		if(typeArgs.size() != 1) {
			throw new ModuleFormatException("Incorrect type arguments for WasmTable");
		}


		var sizeLimitsEnc = method.findAttribute(Attributes.runtimeVisibleAnnotations())
			.flatMap(s -> s
				.annotations()
				.stream()
				.filter(ann -> ann.classSymbol().equals(wasmExportAnn))
				.findFirst()
			)
			.orElse(null);
		if(sizeLimitsEnc == null) {
			throw new ModuleFormatException("Missing SizeLimits for table type");
		}

		var sizeLimits = decodeReturnTypeSizeLimits(method);

		var elementSig = switch(typeArgs.getFirst()) {
			case Signature.TypeArg.Bounded bounded -> {
				if(bounded.wildcardIndicator() != Signature.TypeArg.Bounded.WildcardIndicator.NONE) {
					throw new ModuleFormatException("Invalid wildcard in table type element");
				}

				yield bounded.boundType();
			}
			case Signature.TypeArg.Unbounded _ -> throw new ModuleFormatException("Invalid wildcard in table type element");
		};


		var elementType = loadRefType(elementSig, getReturnTypeArgAnns(method, 0));
		return new TableType(sizeLimits.addressType(), new Limits(sizeLimits.min(), sizeLimits.max() == -1L ? null : sizeLimits.max()), elementType);
	}

	private GlobalType loadExternalGlobalType(MethodModel method) throws ModuleFormatException {
		Signature returnType = getMethodSigReturnType(method);

		if(!(returnType instanceof Signature.ClassTypeSig classTypeSig)) {
			throw new ModuleFormatException("Global type must be a class type");
		}

		if(classTypeSig.classDesc().equals(wasmGlobalRefClass)) {
			var typeArgs = classTypeSig.typeArgs();
			if(typeArgs.size() != 1) {
				throw new ModuleFormatException("Incorrect type arguments for GlobalRef");
			}


			var elementSig = switch(typeArgs.getFirst()) {
				case Signature.TypeArg.Bounded bounded -> {
					if(bounded.wildcardIndicator() != Signature.TypeArg.Bounded.WildcardIndicator.NONE) {
						throw new ModuleFormatException("Invalid wildcard in global type element");
					}

					yield bounded.boundType();
				}
				case Signature.TypeArg.Unbounded _ -> throw new ModuleFormatException("Invalid wildcard in global type element");
			};

			var elementType = loadValType(elementSig, getReturnTypeArgAnns(method, 0));
			return new GlobalType(Mut.Var, elementType);
		}
		else if(classTypeSig.classDesc().equals(wasmGlobalI32Class)) {
			return new GlobalType(Mut.Var, NumType.I32);
		}
		else if(classTypeSig.classDesc().equals(wasmGlobalI64Class)) {
			return new GlobalType(Mut.Var, NumType.I64);
		}
		else if(classTypeSig.classDesc().equals(wasmGlobalF32Class)) {
			return new GlobalType(Mut.Var, NumType.F32);
		}
		else if(classTypeSig.classDesc().equals(wasmGlobalF64Class)) {
			return new GlobalType(Mut.Var, NumType.F64);
		}
		else {
			var t = loadValType(returnType, getReturnTypeAnns(method));
			return new GlobalType(Mut.Const, t);
		}
	}

	private MemType loadExternalMemoryType(MethodModel method) throws ModuleFormatException {
		Signature returnType = getMethodSigReturnType(method);

		if(!(returnType instanceof Signature.ClassTypeSig classTypeSig) || classTypeSig.classDesc().equals(wasmMemoryClass)) {
			throw new ModuleFormatException("A memory export must return a WasmMemory. Actual: " + returnType);
		}

		var sizeLimits = decodeReturnTypeSizeLimits(method);

		return new MemType(sizeLimits.addressType(), new Limits(sizeLimits.min(), sizeLimits.max() == -1L ? null : sizeLimits.max()));
	}



	private ValType loadValType(Signature t, TypeAnns typeAnns) throws ModuleFormatException {
		return switch(t) {
			case Signature.BaseTypeSig baseTypeSig ->
				switch(baseTypeSig.baseType()) {
					case 'I' -> NumType.I32;
					case 'J' -> NumType.I64;
					case 'F' -> NumType.F32;
					case 'D' -> NumType.F64;
					default -> throw new ModuleFormatException("Unexpected base type for val type");
				};
			case Signature.ClassTypeSig classTypeSig when classTypeSig.classDesc().equals(v128Class) ->
				VecType.V128;

			case Signature.ClassTypeSig _ -> loadRefType(t, typeAnns);

			default -> throw new ModuleFormatException("Unexpected signature type: " + t);
		};
	}

	private RefType loadRefType(Signature t, TypeAnns typeAnns) throws ModuleFormatException {
		var heapType = loadHeapType(t, typeAnns);
		return new RefType(typeAnns.isNullable(), heapType);
	}

	private HeapType loadHeapType(Signature t, TypeAnns typeAnns) throws ModuleFormatException {
		if(!(t instanceof Signature.ClassTypeSig ctSig)) {
			throw new ModuleFormatException("Invalid heap type: " + t);
		}

		if(ctSig.classDesc().equals(wasmFunctionClass)) {
			if(typeAnns.isNone()) {
				return HeapType.AbstractHeapType.NOFUNC;
			}
			else {
				return HeapType.AbstractHeapType.FUNC;
			}
		}
		else if(ctSig.classDesc().equals(webAssemblyExceptionClass)) {
			if(typeAnns.isNone()) {
				return HeapType.AbstractHeapType.NOEXN;
			}
			else {
				return HeapType.AbstractHeapType.EXN;
			}
		}
		else if(ctSig.classDesc().equals(CD_Object)) {
			if(typeAnns.isExternRef()) {
				if(typeAnns.isNone()) {
					return HeapType.AbstractHeapType.NOEXTERN;
				}
				else {
					return HeapType.AbstractHeapType.EXTERN;
				}
			}
			else {
				if(typeAnns.isNone()) {
					return HeapType.AbstractHeapType.NONE;
				}
				else {
					return HeapType.AbstractHeapType.ANY;
				}
			}
		}
		else if(ctSig.classDesc().equals(wasmEqClass)) {
			return HeapType.AbstractHeapType.EQ;
		}
		else if(ctSig.classDesc().equals(i31Class)) {
			return HeapType.AbstractHeapType.I31;
		}
		else if(ctSig.classDesc().equals(wasmStructClass)) {
			return HeapType.AbstractHeapType.STRUCT;
		}
		else if(ctSig.classDesc().equals(wasmArrayClass)) {
			return HeapType.AbstractHeapType.ARRAY;
		}
		else {
			throw new RuntimeException("Not implemented");
		}
	}


	private @Nullable ClassModel loadClass(ClassDesc classDesc) throws ModuleFormatException {
		try {
			var javaClass = loader.getJavaClass(classDesc);
			if(javaClass == null) return null;
			byte[] b;
			try(var is = javaClass.read()) {
				b = ByteStreams.toByteArray(is);
			}
			return compiler.classFile().parse(b);
		}
		catch(IOException e) {
			throw ioToModuleError(e);
		}
	}

	private static ModuleFormatException ioToModuleError(IOException e) {
		return new ModuleFormatException("IO Error when loading module", e);
	}

	@SuppressWarnings("NullAway")
	private static ModuleFormatException uncheckedIOToModuleError(UncheckedIOException e) {
		return ioToModuleError(e.getCause());
	}


	private boolean isTopLevelClass(ClassModel classModel) {
		String className = classModel.thisClass().name().stringValue();

		for(var element : classModel) {
			if(element instanceof InnerClassesAttribute innerClassesAttr) {
				for(InnerClassInfo innerClassInfo : innerClassesAttr.classes()) {
					if(innerClassInfo.innerClass().name().stringValue().equals(className)) {
						return false;
					}
				}

				break;
			}
		}

		return true;
	}

	private boolean hasInnerClass(ClassModel classModel, String innerClassName) {
		String className = classModel.thisClass().name().stringValue();

		for(var element : classModel) {
			if(element instanceof InnerClassesAttribute innerClassesAttr) {
				for(InnerClassInfo innerClassInfo : innerClassesAttr.classes()) {
					if(
						innerClassInfo.innerName().map(in -> in.stringValue().equals(innerClassName)).orElse(false) &&
						innerClassInfo.outerClass().map(oc -> oc.name().stringValue().equals(className)).orElse(false)
					) {
						return true;
					}
				}

				break;
			}
		}

		return false;
	}

	private boolean implementsInterface(ClassDesc subClass, ClassDesc superInterface) throws ModuleFormatException {
		Set<ClassDesc> seenInterfaces = new HashSet<>();
		Queue<ClassDesc> scanTypes = new ArrayDeque<>();

		seenInterfaces.add(subClass);
		scanTypes.offer(subClass);

		while(true) {
			var st = scanTypes.poll();
			if(st == null) {
				break;
			}

			if(st.equals(superInterface)) {
				return true;
			}

			var classModel = loadClass(st);
			if(classModel == null) {
				return false;
			}

			classModel.interfaces()
				.stream()
				.map(ClassEntry::asInternalName)
				.map(ClassDesc::ofInternalName)
				.forEach(si -> {
					if(seenInterfaces.add(si)) {
						scanTypes.offer(si);
					}
				});
		}

		return false;
	}

	private List<Signature.TypeParam> getClassTypeParameters(ClassModel t) {
		for(var element : t) {
			if(element instanceof SignatureAttribute signatureAttr) {
				return signatureAttr.asClassSignature().typeParameters();
			}
		}
		return List.of();
	}

	private List<Signature> getMethodSigParameters(MethodModel m) {
		for(var element : m) {
			if(element instanceof SignatureAttribute signatureAttr) {
				return signatureAttr.asMethodSignature().arguments();
			}
		}

		return m.methodTypeSymbol().parameterList().stream()
			.map(Signature::of)
			.toList();
	}

	private Signature getMethodSigReturnType(MethodModel m) {
		var sig = m.findAttribute(Attributes.signature()).orElse(null);
		if(sig != null) {
			return sig.asMethodSignature().result();
		}

		return Signature.of(m.methodTypeSymbol().returnType());
	}

	private record TypeAnns(boolean isNullable, boolean isNone, boolean isExternRef) {}

	private static TypeAnns getParameterTypeAnns(MethodModel method, int paramIndex) {
		var anns = method.findAttribute(Attributes.runtimeVisibleTypeAnnotations())
			.map(RuntimeVisibleTypeAnnotationsAttribute::annotations)
			.orElse(List.of());

		boolean isNullable = hasParameterTypeAnnotations(anns, paramIndex, ClassDesc.of("org.jspecify.annotations.Nullable"));
		boolean isNone = hasParameterTypeAnnotations(anns, paramIndex, ClassDesc.of("dev.argon.jawawasm.runtime.NoneType"));
		boolean isExternRef = hasParameterTypeAnnotations(anns, paramIndex, ClassDesc.of("dev.argon.jawawasm.runtime.ExternRef"));

		return new TypeAnns(isNullable, isNone, isExternRef);
	}

	private static boolean hasParameterTypeAnnotations(List<TypeAnnotation> annotations, int paramIndex, ClassDesc annotationClass) {
		for(var ann : annotations) {
			if(
				ann.targetInfo() instanceof TypeAnnotation.FormalParameterTarget formalParamTarget &&
					formalParamTarget.formalParameterIndex() == paramIndex &&
					ann.annotation().classSymbol().equals(annotationClass) &&
					ann.targetPath().isEmpty()
			) {
				return true;
			}
		}

		return false;
	}

	private static TypeAnns getReturnTypeAnns(MethodModel method) {
		var anns = method.findAttribute(Attributes.runtimeVisibleTypeAnnotations())
			.map(RuntimeVisibleTypeAnnotationsAttribute::annotations)
			.orElse(List.of());

		boolean isNullable = hasReturnTypeAnnotations(anns, ClassDesc.of("org.jspecify.annotations.Nullable"));
		boolean isNone = hasReturnTypeAnnotations(anns, ClassDesc.of("dev.argon.jawawasm.runtime.NoneType"));
		boolean isExternRef = hasReturnTypeAnnotations(anns, ClassDesc.of("dev.argon.jawawasm.runtime.ExternRef"));

		return new TypeAnns(isNullable, isNone, isExternRef);
	}

	private static boolean hasReturnTypeAnnotations(List<TypeAnnotation> annotations, ClassDesc annotationClass) {
		for(var ann : annotations) {
			if(
				ann.targetInfo().targetType() == TypeAnnotation.TargetType.METHOD_RETURN &&
					ann.annotation().classSymbol().equals(annotationClass) &&
					ann.targetPath().isEmpty()
			) {
				return true;
			}
		}

		return false;
	}

	private static TypeAnns getReturnTypeArgAnns(MethodModel method, int typeArgIndex) {
		var anns = method.findAttribute(Attributes.runtimeVisibleTypeAnnotations())
			.map(RuntimeVisibleTypeAnnotationsAttribute::annotations)
			.orElse(List.of());

		boolean isNullable = hasReturnTypeArgAnnotations(anns, typeArgIndex, ClassDesc.of("org.jspecify.annotations.Nullable"));
		boolean isNone = hasReturnTypeArgAnnotations(anns, typeArgIndex, ClassDesc.of("dev.argon.jawawasm.runtime.NoneType"));
		boolean isExternRef = hasReturnTypeArgAnnotations(anns, typeArgIndex, ClassDesc.of("dev.argon.jawawasm.runtime.ExternRef"));

		return new TypeAnns(isNullable, isNone, isExternRef);
	}

	private static boolean hasReturnTypeArgAnnotations(List<TypeAnnotation> annotations, int typeArgIndex, ClassDesc annotationClass) {
		for(var ann : annotations) {
			if(
				ann.targetInfo().targetType() == TypeAnnotation.TargetType.METHOD_RETURN &&
					ann.annotation().classSymbol().equals(annotationClass) &&
					ann.targetPath().size() == 1 &&
					ann.targetPath().getFirst().typePathKind().equals(TypeAnnotation.TypePathComponent.Kind.TYPE_ARGUMENT) &&
					ann.targetPath().getFirst().typeArgumentIndex() == typeArgIndex
			) {
				return true;
			}
		}

		return false;
	}


	private record WasmExportImpl(
		String rename,
		boolean allowEmptyName,
		ExportType type
	) implements WasmExport {
		@Override
		public Class<? extends java.lang.annotation.Annotation> annotationType() {
			return WasmExport.class;
		}
	}

	private record SizeLimitsImpl(AddrType addressType, long min, long max) implements SizeLimits {
		@Override
		public Class<? extends java.lang.annotation.Annotation> annotationType() {
			return SizeLimits.class;
		}
	}
}
