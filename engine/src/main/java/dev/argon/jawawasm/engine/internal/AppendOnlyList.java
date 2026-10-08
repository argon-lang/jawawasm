package dev.argon.jawawasm.engine.internal;

import org.jspecify.annotations.Nullable;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.RandomAccess;

/**
 * An append-only list with constant-time clones sharing their backing array.
 * A clone detaches on its first append; the original can continue appending
 * to its array without affecting the clone. Elements are shallowly shared.
 * This class is not thread-safe.
 * @param <E> the element type
 * @hidden
 */
public final class AppendOnlyList<E extends @Nullable Object> extends AbstractList<E> implements RandomAccess, Cloneable {
	private @Nullable Object[] elements;
	private int size;
	private boolean shared;

	public AppendOnlyList() {
		elements = new Object[0];
	}

	private AppendOnlyList(AppendOnlyList<E> source) {
		elements = source.elements;
		size = source.size;
		shared = true;
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	// Slots below size contain only E values supplied through add/addAll.
	@SuppressWarnings({"unchecked", "NullAway"})
	public E get(int index) {
		Objects.checkIndex(index, size);
		return (E) elements[index];
	}

	@Override
	public boolean add(E element) {
		ensureWritable(Math.addExact(size, 1));
		elements[size++] = element;
		++modCount;
		return true;
	}

	@Override
	public void add(int index, E element) {
		if(index != size) {
			throw new UnsupportedOperationException("Only appending is supported");
		}
		add(element);
	}

	@Override
	public boolean addAll(Collection<? extends E> values) {
		int appendedSize = values.size();
		if(appendedSize == 0) {
			return false;
		}
		ensureWritable(Math.addExact(size, appendedSize));
		int nextIndex = size;
		// Keep size and modCount unchanged while iterating so self-appends and
		// appends from this list's views see only the original elements.
		for(E value : values) {
			elements[nextIndex++] = value;
		}
		size = nextIndex;
		++modCount;
		return true;
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> values) {
		if(index != size) {
			throw new UnsupportedOperationException("Only appending is supported");
		}
		return addAll(values);
	}

	@Override
	public AppendOnlyList<E> clone() {
		return new AppendOnlyList<>(this);
	}

	private void ensureWritable(int required) {
		if(shared || required > elements.length) {
			int capacity = elements.length;
			if(required > capacity) {
				long grownCapacity = (long) capacity + capacity / 2;
				capacity = (int) Math.max(required, Math.min(grownCapacity, Integer.MAX_VALUE));
			}
			elements = Arrays.copyOf(elements, capacity);
			shared = false;
		}
	}
}
