package dev.argon.jawawasm.engine.tests.compiler;

import com.google.common.collect.ImmutableList;
import dev.argon.jawawasm.engine.compiler.*;
import dev.argon.jawawasm.engine.validator.ModuleValidator;
import dev.argon.jawawasm.format.instructions.*;
import dev.argon.jawawasm.format.modules.*;
import dev.argon.jawawasm.format.modules.Module;
import dev.argon.jawawasm.format.types.*;
import dev.argon.jawawasm.runtime.AddrType;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class FallbackEmitterTest {
	private static final LabelIdx ZERO = new LabelIdx(0);
	private static final TypeIdx CALLEE_TYPE = new TypeIdx(1);
	private static final FuncIdx CALLEE = new FuncIdx(1);
	private static final TagIdx TAG = new TagIdx(0);
	private static final ControlInstr.BlockType EMPTY = new ControlInstr.BlockType.Empty();
	private static final ControlInstr.BlockType I32 = new ControlInstr.BlockType.OfValType(NumType.I32);
	private static final RefType EQ = new RefType(true, HeapType.AbstractHeapType.EQ);
	private static final RefType I31 = new RefType(false, HeapType.AbstractHeapType.I31);
	private static final RefType EXN = new RefType(false, HeapType.AbstractHeapType.EXN);

	private record Case(String name, Class<? extends ControlInstr> control, ImmutableList<Instr> body, boolean traps) {}

	private static NumericInstr.I32_Const constant(int value) { return new NumericInstr.I32_Const(value); }
	private static VariableInstr.Local_Get argument() { return new VariableInstr.Local_Get(new LocalIdx(0)); }
	private static ImmutableList<Instr> body(Instr... instructions) { return ImmutableList.copyOf(instructions); }
	private static ControlInstr.Block block(ControlInstr.BlockType type, Instr... instructions) {
		return new ControlInstr.Block(type, body(instructions));
	}
	private static Case example(ControlInstr control, Instr... instructions) {
		return new Case(control.getClass().getSimpleName(), control.getClass(), body(instructions), false);
	}
	private static ControlInstr.Try_Table catching(ControlInstr.CatchClause clause, Instr... instructions) {
		return new ControlInstr.Try_Table(EMPTY, ImmutableList.of(clause), body(instructions));
	}

	private static List<Case> cases() {
		var cases = new ArrayList<Case>();
		var nop = new ControlInstr.Nop();
		cases.add(example(nop, nop, constant(42)));
		cases.add(new Case("Unreachable", ControlInstr.Unreachable.class, body(new ControlInstr.Unreachable()), true));
		var block = block(I32, constant(42));
		cases.add(example(block, block));
		var loop = new ControlInstr.Loop(I32, body(argument(), constant(1),
			new NumericInstr.Inn_IBinOp(NumericInstr.NumSize._32, NumericInstr.IBinOp.SUB),
			new VariableInstr.Local_Tee(new LocalIdx(0)), new ControlInstr.Br_If(ZERO), constant(42)));
		cases.add(example(loop, constant(3), new VariableInstr.Local_Set(new LocalIdx(0)), loop));
		var conditional = new ControlInstr.If(I32, body(constant(42)), body(constant(43)));
		cases.add(example(conditional, argument(), conditional));
		var branch = new ControlInstr.Br(ZERO);
		cases.add(example(branch, block(I32, constant(42), branch, constant(43))));
		var branchIf = new ControlInstr.Br_If(ZERO);
		cases.add(example(branchIf, block(I32, constant(42), argument(), branchIf, new ParametricInstr.Drop(), constant(43))));
		var branchTable = new ControlInstr.Br_Table(ImmutableList.of(ZERO), new LabelIdx(1));
		cases.add(example(branchTable, block(I32, block(I32, constant(42), argument(), branchTable),
			new ParametricInstr.Drop(), constant(43))));
		cases.add(new Case("EmptyBranchTable", ControlInstr.Br_Table.class,
			body(block(I32, constant(42), argument(), new ControlInstr.Br_Table(ImmutableList.of(), ZERO))), false));
		var largeTableBody = ImmutableList.<Instr>builder().add(constant(42));
		for(int i = 0; i < 20000; ++i) largeTableBody.add(new ControlInstr.Nop());
		largeTableBody.add(argument(), new ControlInstr.Br_Table(ImmutableList.copyOf(Collections.nCopies(12000, ZERO)), ZERO));
		cases.add(new Case("LargeBranchTable", ControlInstr.Br_Table.class,
			body(new ControlInstr.Block(I32, largeTableBody.build())), false));
		var returnInstr = new ControlInstr.Return();
		cases.add(example(returnInstr, constant(42), returnInstr, constant(43)));
		cases.add(new Case("ReturnDropsCategoryTwo", ControlInstr.Return.class,
			body(new NumericInstr.I64_Const(99), constant(42), returnInstr), false));
		cases.add(new Case("BranchStashesPrefixAndDropsMiddle", ControlInstr.Br.class,
			body(new NumericInstr.I64_Const(7), block(I32, new NumericInstr.I64_Const(99), constant(42), branch),
				new VariableInstr.Local_Set(new LocalIdx(0)), new ParametricInstr.Drop(), argument()), false));
		cases.add(new Case("BranchStashesReferenceArgument", ControlInstr.Br.class,
			body(block(new ControlInstr.BlockType.OfValType(I31), new NumericInstr.I64_Const(99), constant(42),
				new ReferenceInstr.Ref_I31(), branch), new ReferenceInstr.I31_Get_S()), false));

		var nullBranch = new ControlInstr.Br_OnNull(ZERO);
		var reference = new ControlInstr.If(new ControlInstr.BlockType.OfValType(EQ),
			body(constant(42), new ReferenceInstr.Ref_I31()), body(new ReferenceInstr.Ref_Null(HeapType.AbstractHeapType.EQ)));
		cases.add(example(nullBranch, block(I32, constant(42), argument(), reference, nullBranch,
			new ParametricInstr.Drop(), new ParametricInstr.Drop(), constant(43))));
		var nonNullBranch = new ControlInstr.Br_OnNonNull(ZERO);
		cases.add(example(nonNullBranch, block(new ControlInstr.BlockType.OfValType(new RefType(false, HeapType.AbstractHeapType.EQ)),
			argument(), reference, nonNullBranch, constant(43), new ReferenceInstr.Ref_I31()),
			new ReferenceInstr.Ref_Cast(I31), new ReferenceInstr.I31_Get_S()));
		var cast = new ControlInstr.Br_OnCast(ZERO, EQ, I31);
		cases.add(example(cast, block(new ControlInstr.BlockType.OfValType(I31), argument(), reference, cast,
			new ParametricInstr.Drop(), constant(43), new ReferenceInstr.Ref_I31()), new ReferenceInstr.I31_Get_S()));
		var castFail = new ControlInstr.Br_OnCastFail(ZERO, EQ, I31);
		cases.add(example(castFail, block(new ControlInstr.BlockType.OfValType(EQ), argument(), reference, castFail),
			new ReferenceInstr.Ref_IsNull()));

		for(boolean tail : new boolean[]{false, true}) {
			ControlInstr direct = tail ? new ControlInstr.Return_Call(CALLEE) : new ControlInstr.Call(CALLEE);
			cases.add(example(direct, direct));
			ControlInstr ref = tail ? new ControlInstr.Return_Call_Ref(CALLEE_TYPE) : new ControlInstr.Call_Ref(CALLEE_TYPE);
			cases.add(example(ref, new ReferenceInstr.Ref_Func(CALLEE), ref));
			ControlInstr indirect = tail ? new ControlInstr.Return_Call_Indirect(new TableIdx(0), CALLEE_TYPE)
				: new ControlInstr.Call_Indirect(new TableIdx(0), CALLEE_TYPE);
			cases.add(example(indirect, constant(0), indirect));
		}

		var throwInstr = new ControlInstr.Throw(TAG);
		var tryTable = catching(new ControlInstr.CatchTag(TAG, ZERO), throwInstr);
		cases.add(example(tryTable, block(EMPTY, tryTable), constant(42)));
		cases.add(example(throwInstr, block(EMPTY, tryTable), constant(42)));
		var protectedBody = ImmutableList.<Instr>builder();
		for(int i = 0; i < 31000; ++i) protectedBody.add(new ControlInstr.Nop());
		protectedBody.add(throwInstr);
		cases.add(new Case("CatchAcrossChunks", ControlInstr.Try_Table.class,
			body(block(EMPTY, new ControlInstr.Try_Table(EMPTY, ImmutableList.of(new ControlInstr.CatchTag(TAG, ZERO)),
				protectedBody.build())), constant(42)), false));
		var throwRef = new ControlInstr.Throw_Ref();
		cases.add(example(throwRef, block(EMPTY, catching(new ControlInstr.CatchAll(ZERO),
			block(new ControlInstr.BlockType.OfValType(EXN), catching(new ControlInstr.CatchAllRef(ZERO), throwInstr),
				new ControlInstr.Unreachable()), throwRef)), constant(42)));
		cases.add(new Case("CatchTagRef", ControlInstr.Try_Table.class,
			body(block(new ControlInstr.BlockType.OfValType(EXN), catching(new ControlInstr.CatchTagRef(TAG, ZERO), throwInstr),
				new ControlInstr.Unreachable()), new ReferenceInstr.Ref_IsNull()), false));
		return cases;
	}

	private static RecursiveType type(ResultType args, ResultType results) {
		return new RecursiveType(ImmutableList.of(new SubType(true, ImmutableList.of(), new FuncType(args, results))));
	}
	private static Module module(Case test) {
		var instructions = ImmutableList.<Instr>builder();
		// Force both fallback selection and an actual size-based split before the control instruction.
		for(int i = 0; i < 31000; ++i) instructions.add(new ControlInstr.Nop());
		instructions.addAll(test.body());
		var empty = new ResultType(ImmutableList.of());
		var i32 = new ResultType(ImmutableList.of(NumType.I32));
		var funcref = new RefType(true, HeapType.AbstractHeapType.FUNC);
		return new Module(
			ImmutableList.of(type(i32, i32), type(empty, i32), type(empty, empty)),
			ImmutableList.of(new Func(new TypeIdx(0), ImmutableList.of(), new Expr(instructions.build())),
				new Func(CALLEE_TYPE, ImmutableList.of(), new Expr(body(constant(42))))),
			ImmutableList.of(new Table(new TableType(AddrType.I32, new Limits(1, 1L), funcref),
				new Expr(body(new ReferenceInstr.Ref_Func(CALLEE))))),
			ImmutableList.of(), ImmutableList.of(new Tag(new TagType(new TypeIdx(2)))), ImmutableList.of(),
			ImmutableList.of(new Elem(funcref, ImmutableList.of(new Expr(body(new ReferenceInstr.Ref_Func(CALLEE)))),
				new ElemMode.Declarative())), ImmutableList.of(), null, ImmutableList.of(),
			ImmutableList.of(new Export("f", new ExportDesc.Func(new FuncIdx(0)))), null);
	}

	@TestFactory
	public Stream<DynamicTest> controlInstructions() {
		var cases = cases();
		assertEquals(new HashSet<>(Arrays.asList(Objects.requireNonNull(ControlInstr.class.getPermittedSubclasses()))),
			new HashSet<>(cases.stream().map(Case::control).toList()), "Cover every control instruction");
		return cases.stream().map(test -> DynamicTest.dynamicTest(test.name(), () -> check(test)));
	}

	private static void check(Case test) throws Exception {
		var module = module(test);
		ModuleValidator.validateModule(module);
		var compiler = new ModuleCompiler(new CompilerOptions(ClassHierarchyResolver.defaultResolver(), "test.generated"));
		var realization = compiler.enqueueModule(module, "Fallback", name -> { throw new AssertionError(name); });
		var classes = new HashMap<String, byte[]>();
		boolean split = false;
		for(var generator = compiler.dequeueGenerator(); generator != null; generator = compiler.dequeueGenerator()) {
			if(generator instanceof WasmClassGenerator classGenerator) {
				var bytes = classGenerator.generate();
				var model = ClassFile.of().parse(bytes);
				classes.put(model.thisClass().asInternalName().replace('/', '.'), bytes);
				if(model.thisClass().asInternalName().contains("_SplitFunc_")) {
					assertTrue(compiler.classFile().verify(bytes).isEmpty(), test.name());
					var chunks = model.methods().stream().filter(m -> m.methodName().stringValue().startsWith("chunk")).toList();
					assertTrue(chunks.size() >= 2, "Expected multiple emitted state methods");
					if(test.name().equals("ReturnDropsCategoryTwo") || test.name().startsWith("BranchStashes")) {
						// ref.i31 uses one scratch store to construct its object; transfers need none.
						long expectedStores = test.name().equals("BranchStashesReferenceArgument") ? 1 : 0;
						assertEquals(expectedStores, chunks.stream().flatMap(m -> m.code().orElseThrow().elementStream())
							.filter(e -> e instanceof StoreInstruction).count(), "Transfers must stash directly into fields");
					}
					var execute = model.methods().stream().filter(m -> m.methodName().equalsString("execute")).findFirst().orElseThrow();
					var calls = execute.code().orElseThrow().elementStream().filter(e -> e instanceof InvokeInstruction invoke
						&& invoke.method().name().stringValue().startsWith("chunk")).count();
					assertEquals(chunks.size(), calls, "execute must dispatch to every emitted chunk");
					split = true;
				}
			}
		}
		assertTrue(split, "Expected split fallback bytecode");
		var loader = new ClassLoader(FallbackEmitterTest.class.getClassLoader()) {
			@Override protected Class<?> findClass(String name) throws ClassNotFoundException {
				var bytes = classes.get(name);
				if(bytes == null) throw new ClassNotFoundException(name);
				return defineClass(name, bytes, 0, bytes.length);
			}
		};
		var generated = loader.loadClass("test.generated.Fallback");
		var constructor = generated.getConstructors()[0];
		var instance = constructor.newInstance(new Object[constructor.getParameterCount()]);
		var export = (WasmExportRealization.OfInstanceMethod)realization.exports().stream()
			.filter(e -> e.exportName().equals("f")).findFirst().orElseThrow();
		var method = generated.getMethod(export.methodName(), int.class);
		for(int selector : new int[]{0, 1, -1, 12000}) {
			if(test.traps()) {
				var failure = assertThrows(InvocationTargetException.class, () -> method.invoke(instance, selector));
				assertInstanceOf(dev.argon.jawawasm.runtime.UnreachableTrap.class, failure.getCause());
				continue;
			}
			var result = method.invoke(instance, selector);
			var completed = Objects.requireNonNull(method.getReturnType().getMethod("get", method.getReturnType()).invoke(null, result));
			int expected = switch(test.name()) {
				case "If", "Br_If", "Br_OnNonNull", "Br_OnCast" -> selector == 0 ? 43 : 42;
				case "Br_Table" -> selector == 0 ? 43 : 42;
				case "Br_OnNull" -> selector == 0 ? 42 : 43;
				case "Br_OnCastFail" -> selector == 0 ? 1 : 0;
				case "CatchTagRef" -> 0;
				default -> 42;
			};
			assertEquals(expected, completed.getClass().getField("item0").get(completed), test.name() + " selector=" + selector);
		}
	}
}
