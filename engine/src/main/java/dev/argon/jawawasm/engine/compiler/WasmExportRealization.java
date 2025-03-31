package dev.argon.jawawasm.engine.compiler;

import dev.argon.jawawasm.format.types.DefType;
import dev.argon.jawawasm.format.types.ExternalType;
import dev.argon.jawawasm.format.types.FuncType;

import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

/**
 * Information about the realization of an export.
 */
public sealed interface WasmExportRealization {
	/**
	 * Gets the export name.
	 * @return The export name.
	 */
	String exportName();

	/**
	 * An export realized by an instance method.
	 * @param exportName The export's name.
	 * @param methodName The name of the method implementing the export.
	 * @param methodType The descriptor of the method implementing the export.
	 * @param externalType The type of the export.
	 */
	record OfInstanceMethod(
		String exportName,
		String methodName,
		MethodTypeDesc methodType,
		ExternalType externalType
	) implements WasmExportRealization {}

	/**
	 * An export realized as an inner class.
	 * @param exportName The name of the export.
	 * @param classDesc The descriptor of the inner class.
	 * @param innerClass The InnerClassInfo describing the inner class.
	 * @param constructorType The descriptor of the constructor.
	 * @param tagFunctionType The tag function type.
	 * @param tagDefType The tag def type.
	 */
	record OfInnerClass(
		String exportName,
		ClassDesc classDesc,
		InnerClassInfo innerClass,
		MethodTypeDesc constructorType,
		FuncType tagFunctionType,
		DefType tagDefType
	) implements WasmExportRealization {}

}
