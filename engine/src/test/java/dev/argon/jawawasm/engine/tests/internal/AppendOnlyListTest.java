package dev.argon.jawawasm.engine.tests.internal;

import dev.argon.jawawasm.engine.internal.AppendOnlyList;

import org.junit.jupiter.api.Test;

import java.util.ConcurrentModificationException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AppendOnlyListTest {
	@Test
	public void deeplyNestedClonesAppendWithoutExponentialGrowth() {
		var list = new AppendOnlyList<Integer>();
		list.add(0);
		for(int i = 1; i <= 100; ++i) {
			var parent = list;
			list = parent.clone();
			list.add(i);
			assertEquals(i, parent.size());
			assertEquals(i + 1, list.size());
		}
		for(int i = 0; i <= 100; ++i)
			assertEquals(i, list.get(i));
	}

	@Test
	public void runsInNamedTestModule() {
		assertEquals("dev.argon.jawawasm.engine.tests", getClass().getModule().getName());
		assertEquals("dev.argon.jawawasm.engine", AppendOnlyList.class.getModule().getName());
	}

	@Test
	public void clonesDetachInBothDirectionsAndAcrossGenerations() {
		var original = new AppendOnlyList<Integer>();
		original.addAll(List.of(1, 2, 3));
		var first = original.clone();
		var second = original.clone();
		first.add(4);
		original.add(5);
		var grandchild = first.clone();
		first.add(6);
		grandchild.add(7);
		assertEquals(List.of(1, 2, 3, 5), original);
		assertEquals(List.of(1, 2, 3), second);
		assertEquals(List.of(1, 2, 3, 4, 6), first);
		assertEquals(List.of(1, 2, 3, 4, 7), grandchild);
	}

	@Test
	@SuppressWarnings("ModifyingCollectionWithItself")
	public void bulkAppendHandlesSelfAndViews() {
		var list = new AppendOnlyList<Integer>();
		list.addAll(List.of(1, 2));
		var snapshot = list.clone();
		list.addAll(list);
		list.addAll(list.subList(1, 3));
		assertEquals(List.of(1, 2, 1, 2, 2, 1), list);
		assertEquals(List.of(1, 2), snapshot);
	}

	@Test
	public void emptyCloneAndGrowthRemainIndependent() {
		var list = new AppendOnlyList<Integer>();
		var empty = list.clone();
		for(int i = 0; i < 100; ++i)
			list.add(i);
		empty.add(-1);
		assertEquals(List.of(-1), empty);
		assertEquals(100, list.size());
		for(int i = 0; i < 100; ++i)
			assertEquals(i, list.get(i));
	}

	@Test
	public void rejectsEditsAndKeepsCloneIteratorsIndependent() {
		var list = new AppendOnlyList<Integer>();
		list.addAll(List.of(1, 2));
		assertThrows(UnsupportedOperationException.class, () -> list.set(0, 3));
		assertThrows(UnsupportedOperationException.class, () -> list.remove(/* index */ 0));
		assertThrows(UnsupportedOperationException.class, list::clear);
		assertThrows(UnsupportedOperationException.class, () -> list.add(0, 3));
		assertThrows(UnsupportedOperationException.class, () -> list.addAll(0, List.of(3)));
		assertThrows(IndexOutOfBoundsException.class, () -> list.get(-1));
		assertThrows(IndexOutOfBoundsException.class, () -> list.get(2));
		var iterator = list.iterator();
		list.clone().add(3);
		assertEquals(1, iterator.next());
		list.add(4);
		assertThrows(ConcurrentModificationException.class, iterator::next);
	}
}
