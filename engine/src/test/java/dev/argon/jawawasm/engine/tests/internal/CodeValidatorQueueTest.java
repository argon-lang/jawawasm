package dev.argon.jawawasm.engine.tests.internal;

import com.google.common.collect.ImmutableList;
import dev.argon.jawawasm.engine.validator.ModuleValidator;
import dev.argon.jawawasm.engine.validator.ValidationException;
import dev.argon.jawawasm.format.instructions.*;
import dev.argon.jawawasm.format.modules.*;
import dev.argon.jawawasm.format.modules.Module;
import dev.argon.jawawasm.format.types.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
public class CodeValidatorQueueTest {
	private static final ResultType EMPTY = new ResultType(ImmutableList.of());
	private static final FuncType VOID = new FuncType(EMPTY, EMPTY);

	@Test
	public void deeplyNestedBodiesDrainWithoutRecursion() throws ValidationException {
		var body = deeplyNestedBody();
		ModuleValidator.validateModule(module(1, VOID, ImmutableList.of(), body));
		ModuleValidator.validateModule(module(20, VOID, ImmutableList.of(), body));
		ModuleValidator.validateModule(module(100, VOID, ImmutableList.of(), body));
	}

	@Test
	public void branchingChildrenDrainAndReportLateFailures() throws ValidationException {
		var children = ImmutableList.<Instr>builder();
		for(int i = 0; i < 2_000; ++i) {
			children.add(new ControlInstr.Block(new ControlInstr.BlockType.Empty(),
				ImmutableList.of(new ControlInstr.Nop())));
		}
		var body = children.build();
		// One substantial root and many empty roots let idle workers consume published children.
		var functions = ImmutableList.<Func>builder();
		functions.add(new Func(new TypeIdx(0), ImmutableList.of(), new Expr(body)));
		for(int i = 1; i < 100; ++i) {
			functions.add(new Func(new TypeIdx(0), ImmutableList.of(), new Expr(ImmutableList.of())));
		}
		var template = module(100, VOID, ImmutableList.of(), body);
		var valid = new Module(template.types(), functions.build(), ImmutableList.of(), ImmutableList.of(),
			ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), null,
			ImmutableList.of(), ImmutableList.of(), null);
		for(int i = 0; i < 5; ++i) ModuleValidator.validateModule(valid);
		children.add(new ControlInstr.Block(new ControlInstr.BlockType.Empty(),
			ImmutableList.of(new NumericInstr.I32_Const(1))));
		assertThrows(ValidationException.class, () ->
			ModuleValidator.validateModule(module(100, VOID, ImmutableList.of(), children.build())));
	}

	@Test
	public void queuedGlobalInitializersRequireConstantInstructions() throws ValidationException {
		for(boolean constant : new boolean[] {true, false}) {
			var body = constant
				? ImmutableList.<Instr>of(new NumericInstr.I32_Const(1))
				: ImmutableList.<Instr>of(new ControlInstr.Nop(), new NumericInstr.I32_Const(1));
			var global = new Global(new GlobalType(Mut.Const, NumType.I32), new Expr(body));
			var module = new Module(ImmutableList.of(), ImmutableList.of(), ImmutableList.of(),
				ImmutableList.of(), ImmutableList.of(), ImmutableList.of(global), ImmutableList.of(),
				ImmutableList.of(), null, ImmutableList.of(), ImmutableList.of(), null);
			if(constant) ModuleValidator.validateModule(module);
			else {
				var ex = assertThrows(ValidationException.class, () -> ModuleValidator.validateModule(module));
				assertEquals("constant expression required", ex.getMessage());
			}
		}
	}

	@Test
	public void tableInitializerChildrenAreValidatedWithoutFunctions() throws ValidationException {
		for(int count : new int[] {1, 20}) {
			for(boolean invalid : new boolean[] {false, true}) {
				var body = ImmutableList.<Instr>builder();
				for(int i = 0; i < count; ++i) {
					body.add(new ControlInstr.Block(new ControlInstr.BlockType.Empty(),
						invalid && i == count - 1
							? ImmutableList.of(new NumericInstr.I32_Const(1))
							: ImmutableList.of(new ControlInstr.Nop())));
				}
				body.add(new ReferenceInstr.Ref_Null(HeapType.AbstractHeapType.EXTERN));
				var table = new Table(new TableType(dev.argon.jawawasm.runtime.AddrType.I32,
					new Limits(0, null), new RefType(true, HeapType.AbstractHeapType.EXTERN)),
					new Expr(body.build()));
				var module = new Module(ImmutableList.of(), ImmutableList.of(), ImmutableList.of(table),
					ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), ImmutableList.of(),
					ImmutableList.of(), null, ImmutableList.of(), ImmutableList.of(), null);
				if(invalid) assertThrows(ValidationException.class, () -> ModuleValidator.validateModule(module));
				else ModuleValidator.validateModule(module);
			}
		}
	}

	@Test
	public void workersFinishWhenNoChildrenAreProduced() throws ValidationException {
		for(int count : new int[] {20, 21, 101}) {
			var module = module(count, VOID, ImmutableList.of(), ImmutableList.of());
			for(int i = 0; i < 20; ++i) ModuleValidator.validateModule(module);
		}
	}

	@Test
	public void invalidChildIsReportedInBothExecutionModes() {
		var body = ImmutableList.<Instr>of(new ControlInstr.Block(new ControlInstr.BlockType.Empty(),
			ImmutableList.of(new NumericInstr.I32_Const(1))));
		for(int count : new int[] {1, 19, 20, 21, 101}) {
			assertThrows(ValidationException.class, () -> ModuleValidator.validateModule(module(count, VOID, ImmutableList.of(), body)));
		}
	}

	@Test
	public void queuedBodyDoesNotSeeLaterLocalInitialization() {
		var reference = new RefType(false, HeapType.AbstractHeapType.EXTERN);
		var signature = new FuncType(new ResultType(ImmutableList.of(reference)), EMPTY);
		var body = ImmutableList.<Instr>of(
			new VariableInstr.Local_Get(new LocalIdx(0)),
			new VariableInstr.Local_Set(new LocalIdx(1)),
			new ControlInstr.Block(new ControlInstr.BlockType.Empty(), ImmutableList.of(
				new VariableInstr.Local_Get(new LocalIdx(2)), new ParametricInstr.Drop())),
			new VariableInstr.Local_Get(new LocalIdx(0)),
			new VariableInstr.Local_Set(new LocalIdx(2)));
		for(int count : new int[] {1, 19, 20, 21, 101}) {
			var ex = assertThrows(ValidationException.class, () ->
				ModuleValidator.validateModule(module(count, signature, ImmutableList.of(reference, reference), body)));
			assertEquals("uninitialized local 2", ex.getMessage());
		}
	}

	@Test
	public void interruptionCancelsAndRestoresInterruptFlag() {
		var module = module(101, VOID, ImmutableList.of(), ImmutableList.of(new ControlInstr.Nop()));
		Thread.currentThread().interrupt();
		try {
			var ex = assertThrows(IllegalStateException.class, () -> ModuleValidator.validateModule(module));
			assertInstanceOf(InterruptedException.class, ex.getCause());
			assertTrue(Thread.currentThread().isInterrupted());
		}
		finally {
			Thread.interrupted();
		}
	}

	private static ImmutableList<Instr> deeplyNestedBody() {
		ImmutableList<Instr> body = ImmutableList.of(new ControlInstr.Nop());
		for(int i = 0; i < 2_000; ++i) {
			body = ImmutableList.of(new ControlInstr.Block(new ControlInstr.BlockType.Empty(), body));
		}
		return body;
	}

	private static Module module(int count, FuncType type, ImmutableList<ValType> locals, ImmutableList<Instr> body) {
		var functions = ImmutableList.<Func>builderWithExpectedSize(count);
		for(int i = 0; i < count; ++i) functions.add(new Func(new TypeIdx(0), locals, new Expr(body)));
		return new Module(
			ImmutableList.of(new RecursiveType(ImmutableList.of(new SubType(true, ImmutableList.of(), type)))),
			functions.build(), ImmutableList.of(), ImmutableList.of(), ImmutableList.of(),
			ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), null, ImmutableList.of(), ImmutableList.of(), null);
	}
}
