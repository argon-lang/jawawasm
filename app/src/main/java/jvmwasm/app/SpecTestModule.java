package jvmwasm.app;

import jvmwasm.engine.*;
import jvmwasm.format.types.*;
import org.jspecify.annotations.Nullable;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpecTestModule implements WasmModule {

	public SpecTestModule(Engine engine, PrintWriter output) {
		this.output = output;
		exports.put("global_i32", new WasmGlobal(new GlobalType(Mut.Const, NumType.I32), 666));
		exports.put("global_i64", new WasmGlobal(new GlobalType(Mut.Const, NumType.I64), 666L));
		exports.put("global_f32", new WasmGlobal(new GlobalType(Mut.Const, NumType.F32), 666.6f));
		exports.put("global_f64", new WasmGlobal(new GlobalType(Mut.Const, NumType.F64), 666.6));
		exports.put("memory", WasmMemory.create(engine, new MemType(new Limits(1, 2))));
		exports.put("table", new WasmTable(new TableType(new Limits(10, 20), new FuncRef())));
		exports.put("print", new Print());
		exports.put("print_i32", new PrintI32());
		exports.put("print_i64", new PrintI64());
		exports.put("print_f32", new PrintF32());
		exports.put("print_f64", new PrintF64());
		exports.put("print_i32_f32", new PrintI32F32());
		exports.put("print_f64_f64", new PrintF64F64());
	}
	
	private final PrintWriter output;

	private final Map<String, WasmExport> exports = new HashMap<>();

	@Override
	public @Nullable WasmExport getExport(String name) throws Throwable {
		return exports.get(name);
	}

	private final class Print implements WasmFunction {
		@Override
		public FuncType type() {
			return new FuncType(new ResultType(List.of()), new ResultType(List.of()));
		}

		@Override
		public FunctionResult invoke(Object[] args) throws Throwable {
			output.println();
			return new FunctionResult.Values(new Object[] {});
		}
	}

	private final class PrintI32 implements WasmFunction {
		@Override
		public FuncType type() {
			return new FuncType(new ResultType(List.of(NumType.I32)), new ResultType(List.of()));
		}

		@Override
		public FunctionResult invoke(Object[] args) throws Throwable {
			int n = (int)args[0];
			output.println(n);
			return new FunctionResult.Values(new Object[] {});
		}
	}

	private final class PrintI64 implements WasmFunction {
		@Override
		public FuncType type() {
			return new FuncType(new ResultType(List.of(NumType.I64)), new ResultType(List.of()));
		}

		@Override
		public FunctionResult invoke(Object[] args) throws Throwable {
			long n = (long)args[0];
			output.println(n);
			return new FunctionResult.Values(new Object[] {});
		}
	}

	private final class PrintF32 implements WasmFunction {
		@Override
		public FuncType type() {
			return new FuncType(new ResultType(List.of(NumType.F32)), new ResultType(List.of()));
		}

		@Override
		public FunctionResult invoke(Object[] args) throws Throwable {
			float n = (float)args[0];
			output.println(n);
			return new FunctionResult.Values(new Object[] {});
		}
	}

	private final class PrintF64 implements WasmFunction {
		@Override
		public FuncType type() {
			return new FuncType(new ResultType(List.of(NumType.F64)), new ResultType(List.of()));
		}

		@Override
		public FunctionResult invoke(Object[] args) throws Throwable {
			double n = (double)args[0];
			output.println(n);
			return new FunctionResult.Values(new Object[] {});
		}
	}

	private final class PrintI32F32 implements WasmFunction {
		@Override
		public FuncType type() {
			return new FuncType(new ResultType(List.of(NumType.I32, NumType.F32)), new ResultType(List.of()));
		}

		@Override
		public FunctionResult invoke(Object[] args) throws Throwable {
			int n = (int)args[0];
			float m = (float)args[1];
			output.println(n);
			output.println(m);
			return new FunctionResult.Values(new Object[] {});
		}
	}

	private final class PrintF64F64 implements WasmFunction {
		@Override
		public FuncType type() {
			return new FuncType(new ResultType(List.of(NumType.F64, NumType.F64)), new ResultType(List.of()));
		}

		@Override
		public FunctionResult invoke(Object[] args) throws Throwable {
			double n = (double)args[0];
			double m = (double)args[1];
			output.println(n);
			output.println(m);
			return new FunctionResult.Values(new Object[] {});
		}
	}
}
