package dev.argon.jawawasm.engine;

final class Lazy<T> {
	public Lazy(Initializer<T> initializer) {
		this.initializer = initializer;
	}

	private final Initializer<T> initializer;
	private T value;

	public synchronized T get() throws Throwable {
		if(value == null) {
			value = initializer.init();
		}

		return value;
	}

	public static interface Initializer<T> {
		T init() throws Throwable;
	}
}
