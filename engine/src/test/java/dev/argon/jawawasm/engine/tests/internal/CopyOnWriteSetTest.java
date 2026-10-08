package dev.argon.jawawasm.engine.tests.internal;

import dev.argon.jawawasm.engine.internal.CopyOnWriteSet;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;

import java.util.ConcurrentModificationException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CopyOnWriteSetTest {
	@Test
	void clonesDetachInBothDirectionsAndAcrossGenerations() {
		var original = new CopyOnWriteSet<String>();
		original.addAll(Set.of("a", "b"));
		var first = original.clone();
		var second = original.clone();
		first.add("c");
		original.remove("b");
		var child = first.clone();
		first.clear();
		child.add("d");
		assertEquals(Set.of("a"), original);
		assertEquals(Set.of("a", "b"), second);
		assertTrue(first.isEmpty());
		assertEquals(Set.of("a", "b", "c", "d"), child);
	}

	@Test
	void iteratorRemovalDetachesAndContinues() {
		var set = new CopyOnWriteSet<String>();
		set.addAll(Set.of("a", "b", "c"));
		var iterator = set.iterator();
		assertThrows(IllegalStateException.class, iterator::remove);
		iterator.next();
		var copy = set.clone();
		iterator.remove();
		assertThrows(IllegalStateException.class, iterator::remove);
		while(iterator.hasNext()) {
			iterator.next();
			iterator.remove();
		}
		assertTrue(set.isEmpty());
		assertEquals(Set.of("a", "b", "c"), copy);
		set.add("d");
		var unsharedIterator = set.iterator();
		unsharedIterator.next();
		unsharedIterator.remove();
		assertTrue(set.isEmpty());
	}

	@Test
	void bulkMutationsDetach() {
		var set = new CopyOnWriteSet<Integer>();
		set.addAll(Set.of(1, 2, 3, 4, 5));
		var original = set.clone();
		assertTrue(set.removeAll(Set.of(1, 2)));
		assertTrue(set.retainAll(Set.of(3, 4)));
		assertTrue(set.removeIf(value -> value == 3));
		assertEquals(Set.of(4), set);
		assertEquals(Set.of(1, 2, 3, 4, 5), original);
		var retained = set.clone();
		assertTrue(set.removeAll(set.clone()));
		assertTrue(set.isEmpty());
		assertEquals(Set.of(4), retained);
	}

	@Test
	void nullsAndNoOpMutationsFollowSetSemantics() {
		var set = new CopyOnWriteSet<@Nullable String>();
		assertTrue(set.add(null));
		var copy = set.clone();
		var iterator = set.iterator();
		assertFalse(set.add(null));
		assertFalse(set.remove("missing"));
		assertNull(iterator.next());
		assertTrue(set.remove(null));
		assertTrue(set.isEmpty());
		assertTrue(copy.contains(null));
		assertEquals(1, copy.size());
	}

	@Test
	void iteratorTracksOnlyItsOwnSet() {
		var set = new CopyOnWriteSet<String>();
		set.add("a");
		var iterator = set.iterator();
		set.clone().add("b");
		assertEquals("a", iterator.next());
		set.add("c");
		assertThrows(ConcurrentModificationException.class, iterator::next);
	}
}
