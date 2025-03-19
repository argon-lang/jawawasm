package dev.argon.jawawasm.engine.compiler;

import dev.argon.jawawasm.engine.ModuleResolver;
import dev.argon.jawawasm.format.modules.Module;
import dev.argon.jawawasm.format.modules.TypeIdx;
import dev.argon.jawawasm.format.types.*;
import org.jspecify.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import static java.lang.constant.ConstantDescs.*;

public class ModuleCompiler {
	public ModuleCompiler(CompilerOptions options) {
		this.options = options;
	}

	private final CompilerOptions options;

	private final ReentrantLock lock = new ReentrantLock();

	private final Queue<WasmClassGenerator> generatorQueue = new ConcurrentLinkedQueue<>();


	private final Map<DefType, ClassDesc> typeCache = new ConcurrentHashMap<>();
	private final AtomicInteger funcTypeIndex = new AtomicInteger(0);

	private final Map<ResultType, ClassDesc> resultTypeCache = new ConcurrentHashMap<>();


	public ModuleClassGenerator enqueueModule(Module module, String className, ModuleResolver<ModuleClassGenerator> mappedResolver) throws InterruptedException {
		var gen = new ModuleClassGenerator(this, module, className);
		generatorQueue.offer(gen);
		return gen;
	}

	public @Nullable WasmClassGenerator dequeueGenerator() {
		return generatorQueue.poll();
	}


	CompilerOptions getOptions() {
		return options;
	}


	ClassDesc getDefType(DefType type) {
		return typeCache.computeIfAbsent(type, t -> {
			var subtype = t.recursiveType().subtypes().get(t.index());
			return switch(subtype.compositeType()) {
				case AggregateType aggregateType -> throw new RuntimeException("Not implemented");
				case FuncType funcType -> {
					var className = "Func" + funcTypeIndex.getAndIncrement();
					var generator = new FuncClassGenerator(this, subtype, funcType, className);
					generatorQueue.offer(generator);
					yield generator.className();
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
		throw new RuntimeException("Not implemented");
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

	ClassDesc getTableType(ValType elementType) {
		throw new RuntimeException("Not implemented");
	}

	ClassDesc getGlobalType(ValType elementType) {
		throw new RuntimeException("Not implemented");
	}
}
