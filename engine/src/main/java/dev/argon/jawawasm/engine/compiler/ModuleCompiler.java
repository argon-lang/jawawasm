package dev.argon.jawawasm.engine.compiler;

import com.google.common.collect.ImmutableList;
import dev.argon.jawawasm.engine.ModuleResolver;
import dev.argon.jawawasm.engine.internal.TypeUnroll;
import dev.argon.jawawasm.engine.reflection.ReflectionEngine;
import dev.argon.jawawasm.format.ModuleFormatException;
import dev.argon.jawawasm.format.modules.Module;
import dev.argon.jawawasm.format.modules.TypeIdx;
import dev.argon.jawawasm.format.types.*;
import dev.argon.jawawasm.runtime.ModuleLinkException;
import dev.argon.jawawasm.runtime.ModuleResolutionException;
import org.jspecify.annotations.Nullable;

import java.lang.classfile.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.argon.jawawasm.engine.compiler.Constants.RUNTIME_PACKAGE;
import static dev.argon.jawawasm.engine.compiler.WasmClassGeneratorUtils.*;
import static java.lang.constant.ConstantDescs.*;

/**
 * WebAssembly Module to Java Bytecode compiler.
 */
public class ModuleCompiler {
	/**
	 * Create a WebAssembly module compiler.
	 * @param options The compiler options.
	 */
	public ModuleCompiler(CompilerOptions options) {
		this.options = options;

		classFile = ClassFile.of(
			ClassFile.ShortJumpsOption.FIX_SHORT_JUMPS,
			ClassFile.ClassHierarchyResolverOption.of(
				new GeneratedHierarchyResolver().orElse(options.classHierarchyResolver())
			)
		);
	}

	private final CompilerOptions options;
	private final ClassFile classFile;

	private final Queue<WasmOutputGenerator> generatorQueue = new ConcurrentLinkedQueue<>();
	private final Map<ClassDesc, ClassHierarchyResolver.ClassHierarchyInfo> generatedHierarchy = new ConcurrentHashMap<>();


	private final Map<DefType, DefTypeClassGenerator> typeCache = new ConcurrentHashMap<>();
	private final AtomicInteger funcTypeIndex = new AtomicInteger(0);
	private final AtomicInteger arrayTypeIndex = new AtomicInteger(0);
	private final AtomicInteger structTypeIndex = new AtomicInteger(0);

	private final Map<ErasedResultType, ClassDesc> resultTypeCache = new ConcurrentHashMap<>();

	/**
	 * Enqueue a module for compilation.
	 * @param module The module to compile.
	 * @param className The name of the class for the module.
	 * @param resolver The module resolver used to look up imports.
	 * @return The realization of the module.
	 * @throws ModuleLinkException if a linkage error occurs
	 */
	public WasmModuleRealization enqueueModule(Module module, String className, ModuleResolver<WasmModuleRealization> resolver) {
		var gen = new ModuleClassGenerator(this, module, className, resolver);
		gen.generate();
		enqueueGenerator(gen);
		return gen.realization();
	}

	void enqueueGenerator(WasmOutputGenerator generator) {
		generatorQueue.offer(generator);
		if(generator instanceof WasmClassGenerator cg) {
			generatedHierarchy.put(cg.className(), cg.hierarchyInfo());
		}
	}

	/**
	 * Dequeue a generator.
	 * @return The generator or null if none currently remain.
	 */
	public @Nullable WasmOutputGenerator dequeueGenerator() {
		return generatorQueue.poll();
	}

	/**
	 * Register a class type as a Result type
	 * @param resultType The WebAssembly result type.
	 * @param classDesc The class type.
	 * @throws ModuleFormatException if there is already a known class for this result type.
	 */
	public void registerResultType(ErasedResultType resultType, ClassDesc classDesc) throws ModuleFormatException {
		var cachedClassDesc = resultTypeCache.putIfAbsent(resultType, classDesc);
		if(cachedClassDesc != null && !classDesc.equals(cachedClassDesc)) {
			throw new ModuleFormatException("Conflicting types for result type " + resultType + ": " + classDesc + " and " + cachedClassDesc);
		}
	}

	public ClassFile classFile() {
		return classFile;
	}


	CompilerOptions getOptions() {
		return options;
	}


	DefTypeRealization getDefType(DefType type) {
		return typeCache.computeIfAbsent(type, t -> {
			var subtype = TypeUnroll.unroll(t);
			return switch(subtype.compositeType()) {
				case ArrayType arrayType -> {
					var className = "Array" + arrayTypeIndex.getAndIncrement();
					var generator = new ArrayClassGenerator(this, subtype, arrayType, className);
					enqueueGenerator(generator);
					yield generator;
				}
				case StructType structType -> {
					var className = "Struct" + structTypeIndex.getAndIncrement();
					var generator = new StructClassGenerator(this, subtype, structType, className);
					enqueueGenerator(generator);
					yield generator;
				}
				case FuncType funcType -> {
					var className = "Func" + funcTypeIndex.getAndIncrement();
					var generator = new FuncClassGenerator(this, subtype, funcType, className);
					enqueueGenerator(generator);
					yield generator;
				}
			};
		}).realization();
	}

	TypeRealization getStorageType(StorageType storageType) {
		return switch(storageType) {
			case PackedType packedType -> new TypeRealization(
				switch(packedType) {
					case I8 -> CD_byte;
					case I16 -> CD_short;
				},
				false
			);
			case ValType valType -> getValType(valType);
		};
	}

	TypeRealization getValType(ValType type) {
		return switch(type) {
			case BotType _ -> throw new RuntimeException("Unexpected bot type");
			case NumType numType -> new TypeRealization(
				switch(numType) {
					case I32 -> CD_int;
					case I64 -> CD_long;
					case F32 -> CD_float;
					case F64 -> CD_double;
				},
				false
			);
			case RefType refType -> new TypeRealization(
				getHeapType(refType.heapType()),
				refType.isNullable()
			);
			case VecType vecType -> new TypeRealization(
				switch(vecType) {
					case V128 -> ClassDesc.of("dev.argon.jawawasm.runtime.V128");
				},
				false
			);
		};
	}

	ClassDesc getHeapType(HeapType type) {
		return switch(type) {
			case HeapType.AbstractHeapType abs -> switch(abs) {
				case EXN, NOEXN -> ClassDesc.of(RUNTIME_PACKAGE, "WebAssemblyException");
				case FUNC, NOFUNC -> ClassDesc.of(RUNTIME_PACKAGE, "WasmFunction");
				case EXTERN, NOEXTERN, ANY, NONE -> CD_Object;
				case EQ -> ClassDesc.of(RUNTIME_PACKAGE, "WasmEq");
				case I31 -> i31Type;
				case STRUCT -> ClassDesc.of(RUNTIME_PACKAGE, "WasmStruct");
				case ARRAY -> wasmArray;
			};
			case DefType defType -> getDefType(defType).classDesc();

			case TypeIdx _ -> throw new RuntimeException("Unexpected type index");
			case BotType _ -> throw new RuntimeException("Unexpected bot type");
			case RecTypeIdx _ -> throw new RuntimeException("Unexpected rec type index");
		};
	}

	ResultTypeRealization getResultType(ResultType type) {
		var elementTypeKinds = ImmutableList.<TypeKind>builder();
		var elementTypes = ImmutableList.<TypeRealization>builder();
		var typeArgRealizations = ImmutableList.<TypeRealization>builder();
		List<Signature.TypeArg> typeArgs = new ArrayList<>();

		for(var elem : type.types()) {
			var realizedElem = getValType(elem);

			elementTypes.add(realizedElem);
			elementTypeKinds.add(typeKind(realizedElem.type()));

			if(!realizedElem.type().isPrimitive()) {
				typeArgs.add(Signature.TypeArg.extendsOf(Signature.ClassTypeSig.of(realizedElem.type())));
				typeArgRealizations.add(realizedElem);
			}
		}

		var erasedResType = new ErasedResultType(elementTypeKinds.build());

		var resTypeClass = resultTypeCache.computeIfAbsent(erasedResType, t -> {
			var className = "Result" + funcTypeIndex.getAndIncrement();
			var generator = new ResultClassGenerator(this, t, className);
			enqueueGenerator(generator);
			return generator.className();
		});

		return new ResultTypeRealization(
			resTypeClass,
			Signature.ClassTypeSig.of(resTypeClass, typeArgs.toArray(Signature.TypeArg[]::new)),
			elementTypes.build(),
			typeArgRealizations.build()
		);
	}

	MethodTypeRealization getMethodType(DefType t) {
		var subtype = TypeUnroll.unroll(t);
		return switch(subtype.compositeType()) {
			case AggregateType _ -> throw new RuntimeException("Unexpected aggregate type");
			case FuncType funcType -> getMethodType(funcType);
		};
	}

	MethodTypeRealization getMethodType(FuncType funcType) {
		var paramTypes = ImmutableList.<TypeRealization>builder();
		var argTypes = new ArrayList<ClassDesc>();
		Signature[] sigTypes = new Signature[funcType.args().types().size()];
		for(int i = 0; i < funcType.args().types().size(); ++i) {
			var t = funcType.args().types().get(i);
			var realizedType = getValType(t);
			paramTypes.add(realizedType);
			argTypes.add(realizedType.type());
			sigTypes[i] = classDescToSig(realizedType.type());
		}

		var returnType = getResultType(funcType.results());

		return new MethodTypeRealization(
			MethodTypeDesc.of(returnType.classDesc(), argTypes),
			MethodSignature.of(returnType.signature(), sigTypes),
			returnType,
			paramTypes.build()
		);
	}

	private Signature classDescToSig(ClassDesc desc) {
		if(desc.isPrimitive()) {
			return Signature.BaseTypeSig.of(desc);
		}
		else if(desc.isArray()) {
			return Signature.ArrayTypeSig.of(classDescToSig(desc.componentType()));
		}
		else {
			return Signature.ClassTypeSig.of(desc);
		}
	}

	ClassDesc getGlobalType(ValType elementType) {
		throw new RuntimeException("Not implemented");
	}


	private class GeneratedHierarchyResolver implements ClassHierarchyResolver {
		@Override
		public @Nullable ClassHierarchyInfo getClassInfo(ClassDesc classDesc) {
			return generatedHierarchy.get(classDesc);
		}
	}

}
