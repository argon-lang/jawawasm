package dev.argon.jawawasm.engine.internal;

import org.jspecify.annotations.Nullable;

import java.util.AbstractSet;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;

/**
 * A HashSet-backed set whose constant-time clones share storage until mutation.
 * Elements are shallowly shared. Iterator removal also detaches shared storage.
 * This class is not thread-safe.
 * @param <E> the element type
 * @hidden
 */
public final class CopyOnWriteSet<E extends @Nullable Object> extends AbstractSet<E> implements Cloneable {
	private HashSet<E> backing;
	private boolean shared;
	private int version;

	public CopyOnWriteSet() {
		backing = new HashSet<>();
	}

	private CopyOnWriteSet(CopyOnWriteSet<E> source) {
		backing = source.backing;
		shared = true;
		source.shared = true;
	}

	@Override
	public int size() {
		return backing.size();
	}

	@Override
	public boolean contains(@Nullable Object value) {
		return backing.contains(value);
	}

	@Override
	public boolean add(E value) {
		if(backing.contains(value)) return false;
		ensureWritable();
		backing.add(value);
		++version;
		return true;
	}

	@Override
	public boolean remove(@Nullable Object value) {
		if(!backing.contains(value)) return false;
		ensureWritable();
		backing.remove(value);
		++version;
		return true;
	}

	@Override
	public void clear() {
		if(backing.isEmpty()) return;
		backing = new HashSet<>();
		shared = false;
		++version;
	}

	@Override
	public CopyOnWriteSet<E> clone() {
		return new CopyOnWriteSet<>(this);
	}

	private void ensureWritable() {
		if(shared) {
			backing = new HashSet<>(backing);
			shared = false;
		}
	}

	@Override
	public Iterator<E> iterator() {
		return new Iterator<>() {
			private final HashSet<E> iterated = backing;
			private final Iterator<E> values = iterated.iterator();
			private int expectedVersion = version;
			private @Nullable E current;
			private boolean removable;

			private void checkVersion() {
				if(version != expectedVersion) throw new ConcurrentModificationException();
			}

			@Override
			public boolean hasNext() {
				checkVersion();
				return values.hasNext();
			}

			@Override
			public E next() {
				checkVersion();
				E value = values.next();
				current = value;
				removable = true;
				return value;
			}

			@Override
			@SuppressWarnings("ReferenceEquality") // Iterator removal requires the identical backing set.
			public void remove() {
				checkVersion();
				if(!removable) throw new IllegalStateException();
				ensureWritable();
				if(backing == iterated) values.remove();
				else backing.remove(current);
				++version;
				expectedVersion = version;
				removable = false;
			}
		};
	}
}
