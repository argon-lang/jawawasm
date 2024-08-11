package dev.argon.jawawasm.app;

import dev.argon.jawawasm.engine.*;
import dev.argon.jawawasm.engine.validator.ModuleValidator;
import dev.argon.jawawasm.engine.validator.ValidationException;
import dev.argon.jawawasm.format.ModuleFormatException;
import dev.argon.jawawasm.format.binary.ModuleReader;
import dev.argon.jawawasm.format.data.V128;
import dev.argon.jawawasm.format.modules.Module;
import dev.argon.jawawasm.format.text.SExpr;
import dev.argon.jawawasm.format.text.ScriptCommand;
import dev.argon.jawawasm.format.text.ScriptReader;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;


/**
 * An interpreter for WAST scripts.
 */
public final class ScriptInterpreter implements AutoCloseable {
	/**
	 * Create a ScriptInterpreter.
	 * @param wasmExecutable Path to the reference interpreter.
	 * @param output Writer to receive output.
	 */
	public ScriptInterpreter(Path wasmExecutable, PrintWriter output) {
		this.wasmExecutable = wasmExecutable;
		engine.setMaxMemory(0x10000);
		registeredModules.put("spectest", new SpecTestModule(engine, output));
	}

	private final Engine engine = new Engine();
	private final ModuleResolver resolver = new ScriptResolver();
	private final Path wasmExecutable;

	private WasmModule currentModule = null;
	private Map<String, WasmModule> registeredModules = new HashMap<>();
	private Map<String, WasmModule> namedModules = new HashMap<>();
	private Map<Integer, Object> externRefs = new HashMap<>();

	private static final class F32NanCanonical {}
	private static final class F32NanArithmetic {}

	private static final class F64NanCanonical {}
	private static final class F64NanArithmetic {}

	private static record F32x4Result(Object f0, Object f1, Object f2, Object f3) {}
	private static record F64x2Result(Object f0, Object f1) {}

	private WasmModule getModuleByName(@Nullable String name) throws ScriptExecutionException {
		WasmModule module;
		if(name != null) {
			module = namedModules.get(name);
			if(module == null) {
				throw new ScriptExecutionException("Unknown module: " + name);
			}
		}
		else {
			module = currentModule;
		}

		return module;
	}

	private Object getExternRef(int index) {
		return externRefs.computeIfAbsent(index, k -> new Object());
	}

	/**
	 * Execute a script command.
	 * @param command The command to execute.
	 * @throws ExecutionException if an error occurred while executing WebAssembly.
	 * @throws ScriptExecutionException if an error occurred while executing the script.
	 * @throws ModuleFormatException if a module is malformed.
	 * @throws ValidationException if a module failed validation.
	 * @throws ModuleLinkException if a module link error occurred.
	 * @throws IOException if an IO error occurred.
	 * @throws InterruptedException if execution was interrupted.
	 */
	public void executeCommand(ScriptCommand command) throws ExecutionException, ScriptExecutionException, ModuleFormatException, ValidationException, ModuleLinkException, IOException, InterruptedException {
		switch(command) {
			case ScriptCommand.ScriptModule(var name, var moduleExpr) -> {
				var convertedModule = getModuleAsBinary(moduleExpr);
				ModuleValidator.validateModule(convertedModule);
				var module = engine.instantiateModule(convertedModule, resolver);
				currentModule = module;

				if(name != null) {
					namedModules.put(name, module);
				}
			}

			case ScriptCommand.Register(var importName, var name) -> {
				var module = getModuleByName(name);
				registeredModules.put(importName, module);
			}

			case ScriptCommand.Action action -> {
				runAction(action);
			}

			case ScriptCommand.Assertion.AssertReturn(var action, var results) -> {
				Object[] expected = getConstantValues(results);
				Object[] actual = runAction(action);
				if(!valuesEqual(expected, actual)) {
					throw new ScriptAssertionException("Assertion failed\nAssertion: " + command + "\nExpected: " + Arrays.toString(expected) + "\nActual: " + Arrays.toString(actual));
				}

			}

			case ScriptCommand.Assertion.AssertTrap(var action, var message) ->
				assertTrapIn(() -> runAction(action), message);

			case ScriptCommand.Assertion.AssertExhaustion(var action, var message) -> {
				boolean foundError = false;
				switch(message) {
					case "call stack exhausted" -> {
						try {
							runAction(action);
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

			case ScriptCommand.Assertion.AssertMalformed(var module, var message) -> {
				boolean foundError = false;
				try {
					getModuleAsBinary(module);
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
							skipExpected = true;

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
					throw new ScriptAssertionException("Expected malformed module, but parsing succeeded");
				}
			}

			case ScriptCommand.Assertion.AssertInvalid(var module, var message) -> {
				boolean foundError = false;

				var convertedModule = getModuleAsBinary(module);
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

			case ScriptCommand.Assertion.AssertUnlinkable(var module, var message) -> {
				boolean foundError = false;

				var convertedModule = getModuleAsBinary(module);
				ModuleValidator.validateModule(convertedModule);
				try {
					engine.instantiateModule(convertedModule, resolver);
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
			case ScriptCommand.Assertion.AssertTrapInstantiation(var module, var message) -> {
				var convertedModule = getModuleAsBinary(module);
				ModuleValidator.validateModule(convertedModule);
				assertTrapIn(() -> engine.instantiateModule(convertedModule, resolver), message);
			}
		}
	}

	private boolean valuesEqual(Object[] expected, Object[] actual) {
		if(expected.length != actual.length) {
			return false;
		}

		for(int i = 0; i < expected.length; ++i) {
			if(!valueEqual(expected[i], actual[i])) {
				return false;
			}
		}

		return true;
	}

	private boolean valueEqual(Object expected, Object actual) {
		if(expected instanceof Integer i1 && actual instanceof Integer i2) {
			return (int)i1 == (int)i2;
		}
		else if(expected instanceof Long l1 && actual instanceof Long l2) {
			return (long)l1 == (long)l2;
		}
		else if(expected instanceof Float f1 && actual instanceof Float f2) {
			return Float.floatToRawIntBits(f1) == Float.floatToRawIntBits(f2);
		}
		else if(expected instanceof F32NanCanonical && actual instanceof Float f2) {
			return (Float.floatToRawIntBits(f2) & 0x7FFFFFFF) == 0x7FC00000;
		}
		else if(expected instanceof F32NanArithmetic && actual instanceof Float f2) {
			return (Float.floatToRawIntBits(f2) & 0x7FC00000) == 0x7FC00000;
		}
		else if(expected instanceof Double d1 && actual instanceof Double d2) {
			return Double.doubleToRawLongBits(d1) == Double.doubleToRawLongBits(d2);
		}
		else if(expected instanceof F64NanCanonical && actual instanceof Double d2) {
			return (Double.doubleToRawLongBits(d2) & 0x7FFFFFFFFFFFFFFFL) == 0x7FF8000000000000L;
		}
		else if(expected instanceof F64NanArithmetic && actual instanceof Double d2) {
			return (Double.doubleToRawLongBits(d2) & 0x7FF8000000000000L) == 0x7FF8000000000000L;
		}
		else if(expected instanceof V128 v1 && actual instanceof V128 v2) {
			return v1.equals(v2);
		}
		else if(expected instanceof F32x4Result v1 && actual instanceof V128 v2) {
			return valueEqual(v1.f0(), v2.extractLaneF32(0)) &&
					valueEqual(v1.f1(), v2.extractLaneF32(1)) &&
					valueEqual(v1.f2(), v2.extractLaneF32(2)) &&
					valueEqual(v1.f3(), v2.extractLaneF32(3));
		}
		else if(expected instanceof F64x2Result v1 && actual instanceof V128 v2) {
			return valueEqual(v1.f0(), v2.extractLaneF64(0)) &&
					valueEqual(v1.f1(), v2.extractLaneF64(1));
		}
		else {
			return expected == actual;
		}
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

			case "out of bounds memory access", "out of bounds table access", "undefined element" -> {
				if(error instanceof IndexOutOfBoundsException) {
					gotExpectedError = true;
				}
			}

			case "indirect call type mismatch" -> {
				if(error instanceof IndirectCallTypeMismatchException) {
					gotExpectedError = true;
				}
			}

			case "unreachable" -> {
				if(error instanceof UnreachableException) {
					gotExpectedError = true;
				}
			}

			case String m when m.startsWith("uninitialized element") -> {
				if(error instanceof NullPointerException) {
					gotExpectedError = true;
				}
			}

			default -> throw new ScriptAssertionException("Unknown failure message: " + message);
		}

		if(!gotExpectedError) {
			throw new ScriptAssertionException("Action completed, expected failure: " + message);
		}
	}

	/**
	 * Execute a script.
	 * @param commands The commands in the script.
	 * @throws ExecutionException if an error occurred while executing WebAssembly.
	 * @throws ScriptExecutionException if an error occurred while executing the script.
	 * @throws ModuleFormatException if a module is malformed.
	 * @throws ValidationException if a module failed validation.
	 * @throws ModuleLinkException if a module link error occurred.
	 * @throws IOException if an IO error occurred.
	 * @throws InterruptedException if execution was interrupted.
	 */
	public void executeScript(List<? extends ScriptCommand> commands) throws ExecutionException, ValidationException, ScriptExecutionException, ModuleFormatException, ModuleLinkException, IOException, InterruptedException {
		for(var command : commands) {
			executeCommand(command);
		}
	}

	private Object[] runAction(ScriptCommand.Action action) throws ExecutionException, ScriptExecutionException {
		return switch(action) {
			case ScriptCommand.Action.Invoke(var name, var exportName, var exprs) -> {
				var module = getModuleByName(name);
				var export = (WasmFunction)module.getExport(exportName);
				yield FunctionResult.resolveWith(() -> export.invoke(getConstantValues(exprs)));
			}

			case ScriptCommand.Action.Get(var name, var exportName) -> {
				var module = getModuleByName(name);
				var export = (WasmGlobal)module.getExport(exportName);
				yield new Object[] { export.get() };
			}
		};
	}

	private Module getModuleAsBinary(SExpr expr) throws ModuleFormatException, IOException, InterruptedException {
		var exprs = ((SExpr.ExprList)expr).exprs();
		if(exprs.size() > 2 && exprs.get(1) instanceof SExpr.Identifier binarySpecifier && binarySpecifier.name().equals("binary")) {
			return getModuleAsBinaryLiteral(exprs);
		}
		else {
			return getModuleAsBinaryExternal(expr);
		}
	}

	private Module getModuleAsBinaryLiteral(List<? extends SExpr> exprs) throws ModuleFormatException, IOException {
		var os = new ByteArrayOutputStream();
		for(int i = 2; i < exprs.size(); ++i) {
			((SExpr.StringValue)exprs.get(i)).writeTo(os);
		}

		var is = new ByteArrayInputStream(os.toByteArray());
		return new ModuleReader(is).readModule();
	}

	private Module getModuleAsBinaryExternal(SExpr expr) throws ModuleFormatException, IOException, InterruptedException {
		Path tempIn = Files.createTempFile("wasm-", ".wast");
		try {
			Files.writeString(tempIn, expr.toString());
			Path temp = Files.createTempFile("wasm-", ".wasm");
			try {
				var process = new ProcessBuilder(wasmExecutable.toString(), "-u", "-d", tempIn.toString(), "-o", temp.toString()).start();
				if(process.waitFor() != 0) {
					throw new ModuleConversionException();
				}

				long size = Files.size(temp);
				try(var is = Files.newInputStream(temp)) {
					return new ModuleReader(is).readModule();
				}
			}
			finally {
				Files.delete(temp);
			}
		}
		finally {
			Files.delete(tempIn);
		}
	}



	private final class ScriptResolver implements ModuleResolver {
		@Override
		public WasmModule resolve(String name) throws ModuleResolutionException {
			var module = registeredModules.get(name);
			if(module == null) {
				throw new ModuleResolutionException();
			}
			return module;
		}
	}

	private @Nullable Object getConstantValue(SExpr expr) throws ModuleFormatException {
		var exprs = ((SExpr.ExprList)expr).exprs();
		return switch(ScriptReader.getSExprConstructor(expr)) {
			case "i32.const" -> ((SExpr.NumberValue)exprs.get(1)).intValue();
			case "i64.const" -> ((SExpr.NumberValue)exprs.get(1)).longValue();
			case "f32.const" -> getFloat32LiteralValue(exprs.get(1));
			case "f64.const" -> getFloat64LiteralValue(exprs.get(1));
			case "v128.const" -> {
				String shape = ((SExpr.Identifier)exprs.get(1)).name();
				yield switch(shape) {
					case "i8x16" -> V128.build8(i -> (byte)((SExpr.NumberValue)exprs.get(i + 2)).intValue());
					case "i16x8" -> V128.build16(i -> (short)((SExpr.NumberValue)exprs.get(i + 2)).intValue());
					case "i32x4" -> V128.build32(i -> ((SExpr.NumberValue)exprs.get(i + 2)).intValue());
					case "i64x2" -> V128.build64(i -> ((SExpr.NumberValue)exprs.get(i + 2)).longValue());
					case "f32x4" -> {
						boolean hasSpecialNanCheck = false;
						Object[] values = new Object[4];
						for(int i = 0; i < values.length; ++i) {
							Object value = getFloat32LiteralValue(exprs.get(i + 2));
							values[i] = value;
							if(value instanceof F32NanArithmetic || value instanceof F32NanCanonical) {
								hasSpecialNanCheck = true;
							}
						}

						if(hasSpecialNanCheck) {
							yield new F32x4Result(values[0], values[1], values[2], values[3]);
						}
						else {
							yield V128.buildF32(i -> (float)values[i]);
						}
					}
					case "f64x2" -> {
						boolean hasSpecialNanCheck = false;
						Object[] values = new Object[2];
						for(int i = 0; i < values.length; ++i) {
							Object value = getFloat64LiteralValue(exprs.get(i + 2));
							values[i] = value;
							if(value instanceof F64NanArithmetic || value instanceof F64NanCanonical) {
								hasSpecialNanCheck = true;
							}
						}

						if(hasSpecialNanCheck) {
							yield new F64x2Result(values[0], values[1]);
						}
						else {
							yield V128.buildF64(i -> (double)values[i]);
						}
					}
					default -> throw new ModuleFormatException("Unexpected vector shape in literal: " + shape);
				};
			}
			case "ref.extern" -> getExternRef(((SExpr.NumberValue)exprs.get(1)).intValue());
			case "ref.null" -> null;
			default -> throw new ModuleFormatException("Unexpected constant expression: " + expr);
		};
	}

	private Object getFloat32LiteralValue(SExpr expr) {
		var num = (SExpr.NumberValue)expr;
		if(num.rawNum().equals("nan:canonical")) {
			return new F32NanCanonical();
		}
		else if(num.rawNum().equals("nan:arithmetic")) {
			return new F32NanArithmetic();
		}
		else {
			return num.floatValue();
		}
	}

	private Object getFloat64LiteralValue(SExpr expr) {
		var num = (SExpr.NumberValue)expr;
		if(num.rawNum().equals("nan:canonical")) {
			return new F64NanCanonical();
		}
		else if(num.rawNum().equals("nan:arithmetic")) {
			return new F64NanArithmetic();
		}
		else {
			return num.doubleValue();
		}
	}

	private Object[] getConstantValues(List<? extends SExpr> exprs) throws ModuleFormatException {
		Object[] values = new Object[exprs.size()];
		for(int i = 0; i < exprs.size(); ++i) {
			values[i] = getConstantValue(exprs.get(i));
		}
		return values;
	}

	@Override
	public void close() {
		engine.close();
	}
}
