package dev.argon.jawawasm.engine.reflection;

import com.google.common.collect.ImmutableList;
import dev.argon.jawawasm.engine.compiler.ModuleCompiler;
import dev.argon.jawawasm.engine.compiler.WasmExportRealization;
import dev.argon.jawawasm.engine.compiler.WasmModuleRealization;
import dev.argon.jawawasm.format.ModuleFormatException;
import dev.argon.jawawasm.format.types.*;
import dev.argon.jawawasm.runtime.*;
import org.jspecify.annotations.Nullable;

import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static java.lang.constant.ConstantDescs.CD_void;

/**
 * Module loader using reflection to access types.
 */
public class ReflectionModuleLoader {
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

	/**
	 * Loads a result type.
	 * @param t The result type to load.
	 * @return The WebAssembly result type that the type defines.
	 * @throws ModuleFormatException If the type is not a valid result type.
	 */
	public ResultType loadResultType(Class<?> t) throws ModuleFormatException {
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

		if(!t.isAssignableFrom(ofMethod.getReturnType())) {
			throw new ModuleFormatException("Invalid result type. \"of\" method must return the result type. Result type: " + t + ", Return type: " + ofMethod.getReturnType());
		}

		var resTypes = ImmutableList.<ValType>builder();
		for(var ctorParamType : ofMethod.getAnnotatedParameterTypes()) {
			resTypes.add(loadValType(ctorParamType));
		}

		var resultType = new ResultType(resTypes.build());

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

		if(!WebAssemblyException.class.isAssignableFrom(innerClass)) {
			return null;
		}

		var constructors = Arrays.stream(innerClass.getConstructors())
			.filter(c -> Modifier.isPublic(innerClass.getModifiers()))
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
			new FuncType(new ResultType(paramTypes.build()), new ResultType(ImmutableList.of()))
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
		};
	}

	private DefType loadExternalFunctionType(Method method) throws ModuleFormatException {
		var paramTypes = ImmutableList.<ValType>builder();
		for(var param : method.getAnnotatedParameterTypes()) {
			paramTypes.add(loadValType(param));
		}

		var resultType = loadResultType(method.getReturnType());

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
