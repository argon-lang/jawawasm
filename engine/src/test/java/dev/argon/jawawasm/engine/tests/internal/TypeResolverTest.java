package dev.argon.jawawasm.engine.tests.internal;

import com.google.common.collect.ImmutableList;
import dev.argon.jawawasm.engine.internal.TypeRoll;
import dev.argon.jawawasm.engine.internal.TypeUnroll;
import dev.argon.jawawasm.format.modules.TypeIdx;
import dev.argon.jawawasm.format.types.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TypeResolverTest {
	@Test
	public void numericSignaturesAndAggregateFieldsAreReused() {
		var signature = new ResultType(ImmutableList.of(NumType.I32, NumType.I64));
		var function = new FuncType(signature, signature);
		var field = new FieldType(NumType.I32, Mut.Const);
		for(CompositeType composite : ImmutableList
			.of(function, new StructType(ImmutableList.of(field)), new ArrayType(field))) {
			var group = new RecursiveType(ImmutableList.of(new SubType(true, ImmutableList.of(), composite)));
			assertSame(group, TypeRoll.roll(group, 0));
			assertSame(group, TypeUnroll.unroll(group));
			assertSame(composite, TypeUnroll.expand(new DefType(group, 0)));
		}
	}

	@Test
	public void changedReferencesRebuildOnlyTheirAncestors() {
		var unchanged = new FieldType(NumType.I32, Mut.Const);
		var changed = new FieldType(new RefType(true, new TypeIdx(0)), Mut.Var);
		var fields = ImmutableList.of(unchanged, changed, unchanged);
		var struct = new StructType(fields);
		var subtype = new SubType(true, ImmutableList.of(), struct);
		var group = new RecursiveType(ImmutableList.of(subtype));
		var rolled = TypeRoll.roll(group, 0);
		assertNotSame(group, rolled);
		var rolledStruct = (StructType)rolled.subtypes().getFirst().compositeType();
		assertSame(unchanged, rolledStruct.fields().get(0));
		assertSame(unchanged, rolledStruct.fields().get(2));
		assertEquals(new RefType(true, new RecTypeIdx(0)), rolledStruct.fields().get(1).storageType());
		assertSame(changed, struct.fields().get(1));
		var expanded = (StructType)TypeUnroll.expand(new DefType(rolled, 0));
		assertEquals(new RefType(true, new DefType(rolled, 0)), expanded.fields().get(1).storageType());
		assertSame(unchanged, expanded.fields().get(0));
	}

	@Test
	public void externalReferencesAndSupertypesRemainUnchanged() {
		var args = new ResultType(ImmutableList.of(new RefType(true, new TypeIdx(0))));
		var composite = new FuncType(args, new ResultType(ImmutableList.of()));
		var group = new RecursiveType(
			ImmutableList.of(new SubType(false, ImmutableList.of(new TypeIdx(0)), composite))
		);
		assertSame(group, TypeRoll.roll(group, 1));
	}
}
