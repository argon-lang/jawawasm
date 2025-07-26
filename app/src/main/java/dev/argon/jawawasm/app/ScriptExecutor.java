package dev.argon.jawawasm.app;

import dev.argon.jawawasm.app.wast.*;
import dev.argon.jawawasm.engine.ModuleResolver;
import dev.argon.jawawasm.engine.interpreter.*;
import dev.argon.jawawasm.engine.validator.ModuleValidator;
import dev.argon.jawawasm.engine.validator.ValidationException;
import dev.argon.jawawasm.format.ModuleFormatException;
import dev.argon.jawawasm.format.binary.ModuleReader;
import dev.argon.jawawasm.format.modules.Module;
import dev.argon.jawawasm.runtime.*;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.foreign.Arena;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;


/**
 * An executor for WAST scripts.
 */
public sealed abstract class ScriptExecutor<Mod> implements AutoCloseable permits ScriptReflectionExecutor, ScriptInterpreter {
	ScriptExecutor(WastLoader loader, PrintWriter output) {
		arena = Arena.ofShared();
		allocator = new ArenaMemoryAllocator(arena);

		this.loader = loader;
		this.output = output;

		allocator.setMaxMemory(0x10000);
	}

	private final Arena arena;
	final MemoryAllocator allocator;
	private final ModuleResolver<Mod> resolver = new ScriptResolver();
	private final WastLoader loader;
	private final PrintWriter output;

	private @Nullable Module currentModuleDefinition = null;
	private @Nullable Mod currentModule = null;
	private Map<String, Module> definedModules = new HashMap<>();
	private Map<String, Mod> registeredModules = new HashMap<>();
	private Map<String, Mod> namedModules = new HashMap<>();
	private Map<Integer, Object> externRefs = new HashMap<>();

	abstract Mod getSpecTestModule(PrintWriter output) throws ModuleFormatException, ModuleLinkException;
	abstract Mod instantiateModule(Module module, ModuleResolver<Mod> resolver) throws ExecutionException;
	abstract @Nullable Object[] invokeModuleExport(Mod mod, String name, @Nullable Object[] args) throws ExecutionException;
	abstract @Nullable Object getGlobalExport(Mod mod, String name);


	private Mod getModuleByName(@Nullable String name) throws ScriptExecutionException {
		Mod module;
		if(name != null) {
			module = namedModules.get(name);
			if(module == null) {
				throw new ScriptExecutionException("Unknown module: " + name);
			}
		}
		else {
			module = currentModule;
			Objects.requireNonNull(module);
		}

		return module;
	}

	private static record KnownExternRef(int index) {}

	private Object getExternRef(int index) {
		return externRefs.computeIfAbsent(index, KnownExternRef::new);
	}


	public void initialize() throws ModuleFormatException, ModuleLinkException {
		registeredModules.put("spectest", getSpecTestModule(output));
	}

	public void executeCommand(WastScriptLoaded scriptLoaded, WastCommand command) throws ExecutionException, ScriptExecutionException, ModuleFormatException, ValidationException, ModuleLinkException, IOException, InterruptedException {
		switch(command) {
			case WastCommand.Module moduleCommand -> {
				var convertedModule = getModuleAsBinary(scriptLoaded, moduleCommand.file());
				ModuleValidator.validateModule(convertedModule);

				var module = instantiateModule(convertedModule, resolver);
				currentModule = module;
				currentModuleDefinition = convertedModule;

				if(moduleCommand.name() != null) {
					namedModules.put(moduleCommand.name(), module);
				}
			}

			case WastCommand.ModuleDefinition moduleDefinition -> {
				var convertedModule = getModuleAsBinary(scriptLoaded, moduleDefinition.file());
				ModuleValidator.validateModule(convertedModule);

				currentModuleDefinition = convertedModule;

				if(moduleDefinition.name() != null) {
					definedModules.put(moduleDefinition.name(), convertedModule);
				}
			}

			case WastCommand.ModuleInstance moduleInstance -> {
				Module convertedModule;
				if(moduleInstance.module() == null) {
					convertedModule = currentModuleDefinition;

					if(convertedModule == null) {
						throw new ScriptExecutionException("No module definition available");
					}
				}
				else {
					convertedModule = definedModules.get(moduleInstance.module());

					if(convertedModule == null) {
						throw new ScriptExecutionException("Could not find requested module");
					}
				}

				var module = instantiateModule(convertedModule, resolver);
				currentModule = module;
				if(moduleInstance.instance() != null) {
					namedModules.put(moduleInstance.instance(), module);
				}
			}

			case WastCommand.Register register -> {
				var module = getModuleByName(register.name());
				registeredModules.put(register.as(), module);
			}

			case WastCommand.Action action -> {
				runAction(action.action());
			}

			case WastCommand.AssertReturn assertReturn -> {
				@Nullable Object[] actual = runAction(assertReturn.action());
				if(!valuesEqual(assertReturn.expected(), actual)) {
					throw new ScriptAssertionException("Assertion failed\nAssertion: " + command + "\nExpected: " + assertReturn.expected() + "\nActual: " + Arrays.toString(actual));
				}

			}

			case WastCommand.AssertException assertException -> {
				@Nullable Object[] actual;

				try {
					actual = runAction(assertException.action());
				}
				catch(ExecutionException ex) {
					if(ex.getCause() instanceof WebAssemblyException) {
						return;
					}

					throw ex;
				}

				throw new ScriptAssertionException("Assertion failed\nAssertion: " + command + "\nExpected an exception\nActual: " + Arrays.toString(actual));
			}

			case WastCommand.AssertTrap assertTrap ->
				assertTrapIn(() -> runAction(assertTrap.action()), assertTrap.text());

			case WastCommand.AssertExhaustion assertExhaustion -> {
				var message = assertExhaustion.text();
				boolean foundError = false;
				switch(message) {
					case "call stack exhausted" -> {
						try {
							runAction(assertExhaustion.action());
						}
						catch(ExecutionException ex) {
							if(ex.getCause() instanceof StackOverflowError) {
								foundError = true;
							}
							else {
								throw ex;
							}
						}
					}

					default -> {}
				}

				if(!foundError) {
					throw new ScriptAssertionException("Expected action to exhaust resources, but completed successfully.");
				}
			}

			case WastCommand.AssertMalformed assertMalformed -> {
				if(assertMalformed.file().moduleType() == ModuleType.TEXT && assertMalformed.file().binaryFilename() == null) {
					return;
				}

				var message = assertMalformed.text();
				boolean foundError = false;
				try {
					getModuleAsBinary(scriptLoaded, assertMalformed.file());
				}
				catch(ModuleConversionException ex) {
					foundError = true;
				}
				catch(ModuleFormatException ex) {
					boolean skipExpected = switch(message) {
						case "length out of bounds",
								"unexpected end of section or function",
								"unexpected end",
								"section size mismatch" ->
							true;

						default -> false;
					};

					if(skipExpected || (ex.getMessage() != null && ex.getMessage().startsWith(message))) {
						foundError = true;
					}
					else {
						throw new ScriptAssertionException("Found malformed module, but got unexpected message.\nExpected: " + message + "\nActual: " + ex.getMessage(), ex);
					}
				}

				if(!foundError) {
					throw new ScriptAssertionException("Expected malformed module, but parsing succeeded. Expected error: " + message);
				}
			}

			case WastCommand.AssertInvalid assertInvalid -> {
				var message = assertInvalid.text();
				boolean foundError = false;

				int colonIndex = message.indexOf(":");
				if(colonIndex >= 0) {
					message = message.substring(0, colonIndex);
				}

				var convertedModule = getModuleAsBinary(scriptLoaded, assertInvalid.file());
				try {
					ModuleValidator.validateModule(convertedModule);
				}
				catch(ValidationException ex) {
					if(ex.getMessage() != null && ex.getMessage().startsWith(message)) {
						foundError = true;
					}
					else {
						throw new ScriptAssertionException("Found invalid module, but got unexpected message.\nExpected: " + message + "\nActual: " + ex.getMessage(), ex);
					}
				}

				if(!foundError) {
					throw new ScriptAssertionException("Expected invalid module, but validation succeeded");
				}
			}

			case WastCommand.AssertUnlinkable assertUnlinkable -> {
				var message = assertUnlinkable.text();
				boolean foundError = false;

				var convertedModule = getModuleAsBinary(scriptLoaded, assertUnlinkable.file());
				ModuleValidator.validateModule(convertedModule);

				try {
					try {
						instantiateModule(convertedModule, resolver);
					}
					catch(ExecutionException e) {
						if(e.getCause() instanceof ModuleLinkException linkEx) {
							throw linkEx;
						}
						else {
							throw e;
						}
					}
				}
				catch(ModuleLinkException ex) {
					if(ex.getMessage() != null && ex.getMessage().startsWith(message)) {
						foundError = true;
					}
					else {
						throw new ScriptAssertionException("Found unlinkable module, but got unexpected message.\nExpected: " + message + "\nActual: " + ex.getMessage(), ex);
					}
				}

				if(!foundError) {
					throw new ScriptAssertionException("Expected unlinkable module, but linking succeeded");
				}
			}

			case WastCommand.AssertUninstantiable assertUninstantiable -> {
				var convertedModule = getModuleAsBinary(scriptLoaded, assertUninstantiable.file());
				ModuleValidator.validateModule(convertedModule);
				assertTrapIn(() -> instantiateModule(convertedModule, resolver), assertUninstantiable.text());
			}
		}
	}

	private boolean valuesEqual(List<WastValue> expected, @Nullable Object[] actual) {
		if(expected.size() != actual.length) {
			return false;
		}

		for(int i = 0; i < expected.size(); ++i) {
			if(!valueEqual(expected.get(i), actual[i])) {
				return false;
			}
		}

		return true;
	}

	private boolean valueEqual(WastValue expected, @Nullable Object actual) {
		return switch(expected) {
			case WastValue.I32(var i1) -> actual instanceof Integer i2 && i1.intValue() == i2;
			case WastValue.I64(var l1) -> actual instanceof Long l2 && l1.longValue() == l2;
			case WastValue.F32(var f1) when f1.equals("nan:canonical") -> actual instanceof Float f2 && (Float.floatToRawIntBits(f2) & 0x7FFFFFFF) == 0x7FC00000;
			case WastValue.F32(var f1) when f1.equals("nan:arithmetic") -> actual instanceof Float f2 && (Float.floatToRawIntBits(f2) & 0x7FC00000) == 0x7FC00000;
			case WastValue.F32(var f1) -> actual instanceof Float f2 && new BigInteger(f1).intValue() == Float.floatToRawIntBits(f2);
			case WastValue.F64(var f1) when f1.equals("nan:canonical") -> actual instanceof Double f2 && (Double.doubleToRawLongBits(f2) & 0x7FFFFFFFFFFFFFFFL) == 0x7FF8000000000000L;
			case WastValue.F64(var f1) when f1.equals("nan:arithmetic") -> actual instanceof Double f2 && (Double.doubleToRawLongBits(f2) & 0x7FF8000000000000L) == 0x7FF8000000000000L;
			case WastValue.F64(var f1) -> actual instanceof Double f2 && new BigInteger(f1).longValue() == Double.doubleToRawLongBits(f2);
			case WastValue.V128 v1 -> actual instanceof V128 v2 && switch(v1.laneType()) {
				case I8, I16, I32, I64 -> getV128Value(v1).equals(v2);

				case F32 ->
					IntStream.range(0, 4)
						.allMatch(i -> valueEqual(new WastValue.F32(v1.value().get(i)), v2.extractLaneF32(i)));
				case F64 ->
					IntStream.range(0, 2)
						.allMatch(i -> valueEqual(new WastValue.F64(v1.value().get(i)), v2.extractLaneF64(i)));
			};
			case WastValue.ExternRef(var id) when id == null -> true;
			case WastValue.ExternRef(var id) when id.equals("null") -> actual == null;
			case WastValue.ExternRef(var id) -> actual == getExternRef(new BigInteger(id).intValue());
			case WastValue.FuncRef(var id) when id == null -> actual instanceof DynamicWasmFunction || actual instanceof WasmFunction;
			case WastValue.FuncRef(var id) when id.equals("null") -> actual == null;
			case WastValue.FuncRef(var id) -> throw new RuntimeException("TODO: func ref " + id);
			case WastValue.AnyRef(var id) when id == null -> true;
			case WastValue.AnyRef(var id) when id.equals("null") -> actual == null;
			case WastValue.AnyRef(var id) -> actual == getExternRef(new BigInteger(id).intValue());
			case WastValue.ExnRef(var id) when id == null -> actual instanceof WebAssemblyException;
			case WastValue.ExnRef(var id) when id.equals("null") -> actual == null;
			case WastValue.ExnRef(var id) -> throw new RuntimeException("Invalid exn ref: " + id);
			case WastValue.I31Ref() -> actual instanceof I31;
			case WastValue.EqRef() -> actual instanceof DynamicWasmEq || actual instanceof WasmEq;
			case WastValue.ArrayRef() -> actual instanceof DynamicWasmArray || actual instanceof WasmArray;
			case WastValue.StructRef() -> actual instanceof DynamicWasmStruct || actual instanceof WasmStruct;
			case WastValue.NullRef(),
				 WastValue.NullFuncRef(),
				 WastValue.NullExternRef(),
				 WastValue.NullExnRef(),
				 WastValue.RefNull() ->
				actual == null;

			case WastValue.Either(var values) -> {
				for(var expectedSub : values) {
					if(valueEqual(expectedSub, actual)) {
						yield true;
					}
				}

				yield false;
			}
		};
	}

	private static interface TrapCheck {
		void run() throws ExecutionException, ModuleFormatException, ScriptExecutionException, ModuleLinkException;
	}

	private void assertTrapIn(TrapCheck check, String message) throws ScriptExecutionException, ModuleFormatException, ModuleLinkException {
		boolean gotExpectedError = false;

		Throwable error = null;
		try {
			check.run();
		}
		catch(ExecutionException ex) {
			error = ex.getCause();
		}

		switch(message) {
			case "integer divide by zero", "integer overflow", "invalid conversion to integer" -> {
				if(error instanceof ArithmeticException) {
					gotExpectedError = true;
				}
			}

			case "out of bounds memory access", "out of bounds table access", "undefined element", "out of bounds array access", "out of bounds" -> {
				if(error instanceof IndexOutOfBoundsException || error instanceof NegativeArraySizeException) {
					gotExpectedError = true;
				}
			}

			case "indirect call type mismatch", "indirect call" -> {
				if(error instanceof IndirectCallTypeMismatchTrap || error instanceof ClassCastException) {
					gotExpectedError = true;
				}
			}

			case "unreachable" -> {
				if(error instanceof UnreachableTrap) {
					gotExpectedError = true;
				}
			}

			case String m when
				m.startsWith("uninitialized element") ||
				m.equals("null function reference") ||
				m.equals("null structure reference") ||
				m.equals("null array reference") ||
				m.equals("null reference") ||
				m.equals("null i31 reference") ->
			{
				if(error instanceof NullPointerException) {
					gotExpectedError = true;
				}
			}

			case "cast", "cast failure" -> {
				if(error instanceof WebAssemblyCastTrap || error instanceof ClassCastException) {
					gotExpectedError = true;
				}
			}

			default -> throw new ScriptAssertionException("Unknown failure message: " + message);
		}

		if(!gotExpectedError) {
			if(error == null) {
				throw new ScriptAssertionException("Action completed, expected failure: " + message);
			}
			else {
				throw new ScriptAssertionException("Action completed, but failed with unexpected error. Expected failure: " + message, error);
			}
		}
	}

	public void executeScript(WastScriptLoaded scriptLoaded) throws Exception {
		var script = scriptLoaded.getScript();
		int i = 0;
		for(var command : script.commands()) {
			System.out.println("Executing command " + command);
			try {
				executeCommand(scriptLoaded, command);
			}
			catch(Exception e) {
				throw new Exception("Script " + Path.of(script.sourceFilename()).getFileName() + " failed at #" + i + " on line " + command.line(), e);
			}
			++i;
		}
	}

	private @Nullable Object[] runAction(WastAction action) throws ExecutionException, ScriptExecutionException {
		return switch(action) {
			case WastAction.Invoke invoke -> {
				var module = getModuleByName(invoke.module());
				@Nullable Object[] args;
				try {
					args = getConstantValues(invoke.args());
				}
				catch(Exception ex) {
					throw new ExecutionException(ex);
				}

				yield invokeModuleExport(module, invoke.field(), args);
			}

			case WastAction.Get get -> {
				var module = getModuleByName(get.module());
				yield new @Nullable Object[] { getGlobalExport(module, get.field()) };
			}
		};
	}

	private Module getModuleAsBinary(WastScriptLoaded scriptLoaded, WastFile file) throws ModuleFormatException, IOException, InterruptedException {
		byte[] data;
		if(file.binaryFilename() != null) {
			data = loader.loadModule(scriptLoaded.getDir().resolve(file.binaryFilename()), ModuleType.BINARY);
		}
		else {
			data = loader.loadModule(scriptLoaded.getDir().resolve(file.filename()), file.moduleType());
		}

		var is = new ByteArrayInputStream(data);
		return new ModuleReader(is).readModule();
	}


	private final class ScriptResolver implements ModuleResolver<Mod> {
		@Override
		public Mod resolve(String name) throws ModuleResolutionException {
			var module = registeredModules.get(name);
			if(module == null) {
				throw new ModuleResolutionException();
			}
			return module;
		}
	}

	private @Nullable Object getConstantValue(WastValue value) throws ModuleFormatException {
		return switch(value) {
			case WastValue.I32(var i) -> i.intValue();
			case WastValue.I64(var l) -> l.longValue();
			case WastValue.F32(var bits) -> Float.intBitsToFloat(new BigInteger(bits).intValue());
			case WastValue.F64(var bits) -> Double.longBitsToDouble(new BigInteger(bits).longValue());
			case WastValue.V128 v -> getV128Value(v);
			case WastValue.ExternRef(var id) when id == null -> throw new ModuleFormatException("Unspecified extern ref");
			case WastValue.ExternRef(var id) when id.equals("null") -> null;
			case WastValue.ExternRef(var id) -> getExternRef(new BigInteger(id).intValue());
			case WastValue.FuncRef(var id) when id == null -> throw new ModuleFormatException("Unspecified func ref");
			case WastValue.FuncRef(var id) when id.equals("null") -> null;
			case WastValue.FuncRef(var id) -> throw new RuntimeException("TODO: func ref " + id);
			case WastValue.AnyRef(var id) when id == null -> throw new ModuleFormatException("Unspecified any ref");
			case WastValue.AnyRef(var id) when id.equals("null") -> null;
			case WastValue.AnyRef(var id) -> getExternRef(new BigInteger(id).intValue());
			case WastValue.ExnRef(var id) when id == null -> throw new ModuleFormatException("Unspecified exn ref");
			case WastValue.ExnRef(var id) when id.equals("null") -> null;
			case WastValue.ExnRef(var id) -> throw new ModuleFormatException("Invalid exn ref: " + id);
			case WastValue.I31Ref() -> throw new ModuleFormatException("Unspecified i31 ref");
			case WastValue.EqRef() -> throw new ModuleFormatException("Unspecified eq ref");
			case WastValue.ArrayRef() -> throw new ModuleFormatException("Unspecified array ref");
			case WastValue.StructRef() -> throw new ModuleFormatException("Unspecified struct ref");
			case WastValue.NullRef(),
				 WastValue.NullFuncRef(),
				 WastValue.NullExternRef(),
				 WastValue.NullExnRef(),
				 WastValue.RefNull() ->
				null;

			case WastValue.Either _ -> throw new ModuleFormatException("Unspecified either");
		};
	}

	private V128 getV128Value(WastValue.V128 v) {
		return switch(v.laneType()) {
			case I8 -> V128.build8(i -> new BigInteger(v.value().get(i)).byteValue());
			case I16 -> V128.build16(i -> new BigInteger(v.value().get(i)).shortValue());
			case I32, F32 -> V128.build32(i -> new BigInteger(v.value().get(i)).intValue());
			case I64, F64 -> V128.build64(i -> new BigInteger(v.value().get(i)).longValue());
		};
	}

//	private Object getFloat32LiteralValue(SExpr expr) {
//		var num = (SExpr.NumberValue)expr;
//		if(num.rawNum().equals("nan:canonical")) {
//			return new F32NanCanonical();
//		}
//		else if(num.rawNum().equals("nan:arithmetic")) {
//			return new F32NanArithmetic();
//		}
//		else {
//			return num.floatValue();
//		}
//	}
//
//	private Object getFloat64LiteralValue(SExpr expr) {
//		var num = (SExpr.NumberValue)expr;
//		if(num.rawNum().equals("nan:canonical")) {
//			return new F64NanCanonical();
//		}
//		else if(num.rawNum().equals("nan:arithmetic")) {
//			return new F64NanArithmetic();
//		}
//		else {
//			return num.doubleValue();
//		}
//	}

	private @Nullable Object[] getConstantValues(List<? extends WastValue> exprs) throws ModuleFormatException {
		@Nullable Object[] values = new Object[exprs.size()];
		for(int i = 0; i < exprs.size(); ++i) {
			values[i] = getConstantValue(exprs.get(i));
		}
		return values;
	}

	@Override
	public void close() {
		arena.close();
	}
}
