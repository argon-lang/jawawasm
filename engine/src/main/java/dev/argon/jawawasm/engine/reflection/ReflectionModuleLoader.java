package dev.argon.jawawasm.engine.reflection;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.ClassPath;
import dev.argon.jawawasm.engine.compiler.ErasedResultType;
import dev.argon.jawawasm.engine.compiler.ModuleCompiler;
import dev.argon.jawawasm.engine.compiler.WasmExportRealization;
import dev.argon.jawawasm.engine.compiler.WasmModuleRealization;
import dev.argon.jawawasm.format.ModuleFormatException;
import dev.argon.jawawasm.format.types.*;
import dev.argon.jawawasm.runtime.*;
import org.jspecify.annotations.Nullable;

import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.*;
import java.util.*;

import static java.lang.constant.ConstantDescs.CD_void;

/**
 * Module loader using reflection to access types.
 */
class ReflectionModuleLoader {
	/**
	 * Creates a module loader.
	 * @param compiler The compiler to be notified of loaded types.
	 */
	public ReflectionModuleLoader(ModuleCompiler compiler) {
		this.compiler = compiler;
	}

	private final ModuleCompiler compiler;

	/**
	 * Load a module from a class.
	 * @param moduleClass The class that defines the WebAssembly module.
	 * @return The realized module.
	 * @throws ModuleFormatException If the class defines an invalid module.
	 */
	public WasmModuleRealization loadModule(Class<?> moduleClass) throws ModuleFormatException {

		var exports = ImmutableList.<WasmExportRealization>builder();
		for(var method : moduleClass.getMethods()) {
			var export = loadExport(method);
			if(export != null) {
				exports.add(export);
			}
		}
		for(var innerClass : moduleClass.getDeclaredClasses()) {
			var export = loadExport(innerClass);
			if(export != null) {
				exports.add(export);
			}
		}

		return new WasmModuleRealization(
			ClassDesc.ofDescriptor(moduleClass.descriptorString()),
			exports.build()
		);
	}

	ResultType loadResultType(AnnotatedType t) throws ModuleFormatException {
		Class<?> tClass;
		AnnotatedType[] typeArgs;

		switch(t.getType()) {
			case ParameterizedType pt -> {
				tClass = (Class<?>)pt.getRawType();
				typeArgs = ((AnnotatedParameterizedType)t).getAnnotatedActualTypeArguments();
			}

			case Class<?> ct -> {
				tClass = ct;
				typeArgs = new AnnotatedType[] {};
			}

			default -> {
				throw new ModuleFormatException("Unexpected result type: " + t);
			}
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
					if(typeArgIndex >= typeArgs.length) {
						throw new ModuleFormatException("Result type argument mismatch. Not enough arguments: " + t);
					}

					var elemType = loadValType(typeArgs[typeArgIndex]);
					++typeArgIndex;
					yield elemType;
				}
				default -> throw new ModuleFormatException("Unexpected result type element: " + elementKind);
			};

			elementTypes.add(elementType);
		}

		if(typeArgIndex != typeArgs.length) {
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
	public ErasedResultType loadResultClass(Class<?> t) throws ModuleFormatException {
		if(!t.getPackage().getName().equals("dev.argon.jawawasm.runtime")) {
			throw new ModuleFormatException("Result types must be defined in dev.argon.jawawasm.runtime");
		}

		var endResultClass = Arrays.stream(t.getDeclaredClasses())
			.filter(nested -> nested.getSimpleName().equals("EndResult"))
			.findAny()
			.orElse(null);

		if(endResultClass == null) {
			throw new ModuleFormatException("Invalid result type. Nested class EndResult is missing");
		}

		var ofMethods = Arrays.stream(t.getMethods())
			.filter(m ->
				m.getName().equals("of") &&
					Modifier.isPublic(m.getModifiers()) &&
					Modifier.isStatic(m.getModifiers())
			)
			.toList();
		if(ofMethods.size() != 1) {
			throw new ModuleFormatException("Invalid result type. It must have exactly one static public method named of.");
		}

		var ofMethod = ofMethods.getFirst();

		if(ofMethod.getReturnType() != t) {
			throw new ModuleFormatException("Invalid result type. \"of\" method must return the result type. Result type: " + t + ", Return type: " + ofMethod.getReturnType());
		}

		var typeParams = t.getTypeParameters();
		int typeParamIndex = 0;

		var resTypes = ImmutableList.<TypeKind>builder();
		for(var ctorParamType : ofMethod.getAnnotatedParameterTypes()) {
			TypeKind typeKind;
			if(ctorParamType.getType() == int.class) {
				typeKind = TypeKind.INT;
			}
			else if(ctorParamType.getType() == long.class) {
				typeKind = TypeKind.LONG;
			}
			else if(ctorParamType.getType() == float.class) {
				typeKind = TypeKind.FLOAT;
			}
			else if(ctorParamType.getType() == double.class) {
				typeKind = TypeKind.DOUBLE;
			}
			else if(ctorParamType instanceof TypeVariable<?> tv) {
				if(typeParamIndex >= typeParams.length) {
					throw new ModuleFormatException("Not enough type parameters on result type: " + t.getName());
				}

				if(tv != typeParams[typeParamIndex]) {
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

		if(typeParamIndex != typeParams.length) {
			throw new ModuleFormatException("Too many type parameters for result type: " + t.getName());
		}

		var resultType = new ErasedResultType(resTypes.build());

		var resTypeDesc = ClassDesc.ofDescriptor(t.descriptorString());

		compiler.registerResultType(resultType, resTypeDesc);

		return resultType;
	}

	private @Nullable WasmExportRealization loadExport(Method method) throws ModuleFormatException {
		if(!Modifier.isPublic(method.getModifiers())) {
			return null;
		}

		if(Modifier.isStatic(method.getModifiers())) {
			return null;
		}

		var exportAnn = method.getAnnotation(WasmExport.class);
		if(exportAnn == null) {
			return null;
		}

		String name;
		if(exportAnn.allowEmptyName() || !exportAnn.rename().isEmpty()) {
			name = exportAnn.rename();
		}
		else {
			name = method.getName();
		}

		var type = getMethodDescriptor(method);

		var externalType = loadExternalType(exportAnn.type(), method);

		return new WasmExportRealization.OfInstanceMethod(name, method.getName(), type, externalType);
	}

	private @Nullable WasmExportRealization loadExport(Class<?> innerClass) throws ModuleFormatException {
		if(!Modifier.isPublic(innerClass.getModifiers())) {
			return null;
		}

		if(!Modifier.isStatic(innerClass.getModifiers())) {
			return null;
		}

		if(!WebAssemblyException.class.equals(innerClass.getSuperclass())) {
			return null;
		}

		var constructors = Arrays.stream(innerClass.getConstructors())
			.filter(c -> Modifier.isPublic(c.getModifiers()))
			.toList();

		if(constructors.size() != 1) {
			throw new ModuleFormatException("A WebAssembly exception type is expected to have exactly one public constructor");
		}

		var constructor = constructors.getFirst();


		String name;

		var exportAnn = innerClass.getAnnotation(WasmExport.class);
		if(exportAnn != null && (exportAnn.allowEmptyName() || !exportAnn.rename().isEmpty())) {
			name = exportAnn.rename();
		}
		else {
			name = innerClass.getSimpleName();
		}


		var paramTypes = ImmutableList.<ValType>builder();
		for(var param : constructor.getAnnotatedParameterTypes()) {
			paramTypes.add(loadValType(param));
		}


		List<ClassDesc> realizedParamTypes = new ArrayList<>();
		for(var param : constructor.getParameterTypes()) {
			realizedParamTypes.add(ClassDesc.ofDescriptor(param.descriptorString()));
		}

		var funcType = new FuncType(new ResultType(paramTypes.build()), new ResultType(ImmutableList.of()));

		return new WasmExportRealization.OfInnerClass(
			name,
			ClassDesc.ofDescriptor(innerClass.descriptorString()),
			InnerClassInfo.of(
				ClassDesc.ofDescriptor(innerClass.descriptorString()),
				Optional.of(ClassDesc.ofDescriptor(innerClass.getDeclaringClass().descriptorString())),
				Optional.of(innerClass.getSimpleName()),
				innerClass.getModifiers()
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

	private MethodTypeDesc getMethodDescriptor(Method method) {
		return MethodTypeDesc.of(
			ClassDesc.ofDescriptor(method.getReturnType().descriptorString()),
			Arrays.stream(method.getParameterTypes())
				.map(t -> ClassDesc.ofDescriptor(t.descriptorString()))
				.toList()
		);
	}

	private ExternalType loadExternalType(WasmExport.ExportType exportType, Method method) throws ModuleFormatException {
		return switch(exportType) {
			case FUNC -> loadExternalFunctionType(method);
			case TABLE -> loadExternalTableType(method.getAnnotatedReturnType());
			case GLOBAL -> loadExternalGlobalType(method.getAnnotatedReturnType());
			case MEMORY -> loadExternalMemoryType(method.getAnnotatedReturnType());
		};
	}

	private DefType loadExternalFunctionType(Method method) throws ModuleFormatException {
		var paramTypes = ImmutableList.<ValType>builder();
		for(var param : method.getAnnotatedParameterTypes()) {
			paramTypes.add(loadValType(param));
		}

		var resultType = loadResultType(method.getAnnotatedReturnType());

		var funcType = new FuncType(new ResultType(paramTypes.build()), resultType);

		return new DefType(
			new RecursiveType(ImmutableList.of(
				new SubType(true, ImmutableList.of(), funcType)
			)),
			0
		);
	}

	private TableType loadExternalTableType(AnnotatedType returnType) throws ModuleFormatException {
		if(!(returnType.getType() instanceof ParameterizedType tableType) || tableType.getRawType() != WasmTable.class) {
			throw new ModuleFormatException("A table export must return a WasmTable. Actual: " + returnType);
		}

		if(!(returnType instanceof AnnotatedParameterizedType annTableType)) {
			throw new ModuleFormatException("Missing parameter types for table export");
		}

		var typeArgs = annTableType.getAnnotatedActualTypeArguments();

		if(typeArgs.length != 1) {
			throw new ModuleFormatException("Incorrect type arguments for WasmTable");
		}

		var sizeLimits = returnType.getAnnotation(SizeLimits.class);
		if(sizeLimits == null) {
			throw new ModuleFormatException("Missing SizeLimits for table type");
		}

		var elementType = loadRefType(typeArgs[0]);
		return new TableType(sizeLimits.addressType(), new Limits(sizeLimits.min(), sizeLimits.max() == -1L ? null : sizeLimits.max()), elementType);
	}

	private GlobalType loadExternalGlobalType(AnnotatedType returnType) throws ModuleFormatException {
		if(returnType.getType() == GlobalRef.class) {
			if(!(returnType instanceof AnnotatedParameterizedType annGlobalType)) {
				throw new ModuleFormatException("Missing parameter types for global export");
			}

			var typeArgs = annGlobalType.getAnnotatedActualTypeArguments();
			if(typeArgs.length != 1) {
				throw new ModuleFormatException("Incorrect type arguments for GlobalRef");
			}

			var elementType = loadValType(typeArgs[0]);
			return new GlobalType(Mut.Var, elementType);
		}
		else if(returnType.getType() == GlobalI32.class) {
			return new GlobalType(Mut.Var, NumType.I32);
		}
		else if(returnType.getType() == GlobalI64.class) {
			return new GlobalType(Mut.Var, NumType.I64);
		}
		else if(returnType.getType() == GlobalF32.class) {
			return new GlobalType(Mut.Var, NumType.F32);
		}
		else if(returnType.getType() == GlobalF64.class) {
			return new GlobalType(Mut.Var, NumType.F64);
		}
		else {
			var t = loadValType(returnType);
			return new GlobalType(Mut.Const, t);
		}
	}

	private MemType loadExternalMemoryType(AnnotatedType returnType) throws ModuleFormatException {
		if(returnType.getType() != WasmMemory.class) {
			throw new ModuleFormatException("A memory export must return a WasmMemory. Actual: " + returnType);
		}

		var sizeLimits = returnType.getAnnotation(SizeLimits.class);
		if(sizeLimits == null) {
			throw new ModuleFormatException("Missing SizeLimits for memory type");
		}

		return new MemType(sizeLimits.addressType(), new Limits(sizeLimits.min(), sizeLimits.max() == -1L ? null : sizeLimits.max()));
	}



	private ValType loadValType(AnnotatedType t) throws ModuleFormatException {
		var underlying = t.getType();
		if(underlying == int.class) {
			return NumType.I32;
		}
		else if(underlying == long.class) {
			return NumType.I64;
		}
		else if(underlying == float.class) {
			return NumType.F32;
		}
		else if(underlying == double.class) {
			return NumType.F64;
		}
		else if(underlying == V128.class) {
			return VecType.V128;
		}
		else {
			return loadRefType(t);
		}
	}

	private RefType loadRefType(AnnotatedType t) throws ModuleFormatException {
		boolean isNullable = t.isAnnotationPresent(Nullable.class);
		var heapType = loadHeapType(t);
		return new RefType(isNullable, heapType);
	}

	private HeapType loadHeapType(AnnotatedType t) throws ModuleFormatException {
		if(!(t.getType() instanceof Class<?> cls)) {
			throw new ModuleFormatException("Invalid heap type: " + t);
		}

		if(cls == WasmFunction.class) {
			if(t.isAnnotationPresent(NoneType.class)) {
				return HeapType.AbstractHeapType.NOFUNC;
			}
			else {
				return HeapType.AbstractHeapType.FUNC;
			}
		}
		else if(cls == WebAssemblyException.class) {
			if(t.isAnnotationPresent(NoneType.class)) {
				return HeapType.AbstractHeapType.NOEXN;
			}
			else {
				return HeapType.AbstractHeapType.EXN;
			}
		}
		else if(cls == Object.class) {
			if(t.isAnnotationPresent(ExternRef.class)) {
				if(t.isAnnotationPresent(NoneType.class)) {
					return HeapType.AbstractHeapType.NOEXTERN;
				}
				else {
					return HeapType.AbstractHeapType.EXTERN;
				}
			}
			else {
				if(t.isAnnotationPresent(NoneType.class)) {
					return HeapType.AbstractHeapType.NONE;
				}
				else {
					return HeapType.AbstractHeapType.ANY;
				}
			}
		}
		else if(cls == WasmEq.class) {
			return HeapType.AbstractHeapType.EQ;
		}
		else if(cls == I31.class) {
			return HeapType.AbstractHeapType.I31;
		}
		else if(cls == WasmStruct.class) {
			return HeapType.AbstractHeapType.STRUCT;
		}
		else if(cls == WasmArray.class) {
			return HeapType.AbstractHeapType.ARRAY;
		}
		else {
			throw new RuntimeException("Not implemented");
		}
	}

}
