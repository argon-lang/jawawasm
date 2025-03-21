package dev.argon.jawawasm.engine.compiler;

import dev.argon.jawawasm.engine.ModuleResolver;
import dev.argon.jawawasm.engine.internal.TypeUnroll;
import dev.argon.jawawasm.format.ModuleFormatException;
import dev.argon.jawawasm.format.modules.Module;
import dev.argon.jawawasm.format.modules.TypeIdx;
import dev.argon.jawawasm.format.types.*;
import dev.argon.jawawasm.runtime.ModuleResolutionException;
import org.jspecify.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.argon.jawawasm.engine.compiler.Constants.RUNTIME_PACKAGE;
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
	}

	private final CompilerOptions options;

	private final Queue<WasmOutputGenerator> generatorQueue = new ConcurrentLinkedQueue<>();


	private final Map<DefType, DefTypeRealization> typeCache = new ConcurrentHashMap<>();
	private final AtomicInteger funcTypeIndex = new AtomicInteger(0);

	private final Map<ResultType, ClassDesc> resultTypeCache = new ConcurrentHashMap<>();

	/**
	 * Enqueue a module for compilation.
	 * @param module The module to compile.
	 * @param className The name of the class for the module.
	 * @param resolver The module resolver used to look up imports.
	 * @return The realization of the module.
	 * @throws ModuleResolutionException if an imported module cannot be resolved
	 */
	public WasmModuleRealization enqueueModule(Module module, String className, ModuleResolver<WasmModuleRealization> resolver) throws ModuleResolutionException {
		var gen = new ModuleClassGenerator(this, module, className, resolver);
		generatorQueue.offer(gen);
		gen.generate();
		return gen.realization();
	}

	void enqueueGenerator(WasmOutputGenerator generator) {
		generatorQueue.offer(generator);
	}

	/**
	 * Dequeue a generator.
	 * @return The generator or null if none currently remain.
	 */
	public @Nullable WasmOutputGenerator dequeueGenerator() {
		return generatorQueue.poll();
	}


	CompilerOptions getOptions() {
		return options;
	}


	DefTypeRealization getDefType(DefType type) {
		return typeCache.computeIfAbsent(type, t -> {
			var subtype = TypeUnroll.unroll(t);
			return switch(subtype.compositeType()) {
				case AggregateType aggregateType -> throw new RuntimeException("Not implemented");
				case FuncType funcType -> {
					var className = "Func" + funcTypeIndex.getAndIncrement();
					var generator = new FuncClassGenerator(this, subtype, funcType, className);
					generatorQueue.offer(generator);
					yield generator.realization();
				}
			};
		});
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
				case I31 -> ClassDesc.of(RUNTIME_PACKAGE, "I32");
				case STRUCT -> ClassDesc.of(RUNTIME_PACKAGE, "WasmStruct");
				case ARRAY -> ClassDesc.of(RUNTIME_PACKAGE, "WasmArray");
			};

			case TypeIdx typeIdx -> throw new RuntimeException("Not implemented");
			case BotType botType -> throw new RuntimeException("Unexpected bot type");
			case DefType defType -> getDefType(defType).classDesc();

			case RecTypeIdx recTypeIdx -> throw new RuntimeException("Not implemented");
		};
	}

	void registerResultType(ResultType resultType, ClassDesc classDesc) throws ModuleFormatException {
		var cachedClassDesc = resultTypeCache.putIfAbsent(resultType, classDesc);
		if(cachedClassDesc != null && !classDesc.equals(cachedClassDesc)) {
			throw new ModuleFormatException("Coflicting types for result type " + resultType + ": " + classDesc + " and " + cachedClassDesc);
		}
	}

	ClassDesc getResultType(ResultType type) {
		return resultTypeCache.computeIfAbsent(type, t -> {
			var className = "Result" + funcTypeIndex.getAndIncrement();
			var generator = new ResultClassGenerator(this, t, className);
			generatorQueue.offer(generator);
			return generator.className();
		});
	}

	MethodTypeDesc getMethodType(DefType t) {
		var subtype = t.recursiveType().subtypes().get(t.index());
		return switch(subtype.compositeType()) {
			case AggregateType _ -> throw new RuntimeException("Unexpected aggregate type");
			case FuncType funcType -> getMethodType(funcType);
		};
	}

	MethodTypeDesc getMethodType(FuncType funcType) {
		var argTypes = new ArrayList<ClassDesc>();
		for(var t : funcType.args().types()) {
			argTypes.add(getValType(t).type());
		}

		var returnType = getResultType(funcType.results());

		return MethodTypeDesc.of(returnType, argTypes);
	}

	ClassDesc getGlobalType(ValType elementType) {
		throw new RuntimeException("Not implemented");
	}
}
