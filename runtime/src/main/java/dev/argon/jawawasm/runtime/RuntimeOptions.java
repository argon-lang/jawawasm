package dev.argon.jawawasm.runtime;

/**
 * Immutable options for the WebAssembly runtime.
 */
public final class RuntimeOptions {
	/**
	 * Creates options with no runtime memory limit.
	 */
	public RuntimeOptions() {
		this(0);
	}

	private RuntimeOptions(long maxMemory) {
		this.maxMemory = maxMemory;
	}

	private final long maxMemory;

	/**
	 * Gets the maximum size of each memory in pages.
	 * 
	 * @return The maximum page count, or zero for no runtime limit.
	 */
	public long maxMemory() {
		return maxMemory;
	}

	/**
	 * Creates a builder with default options.
	 * 
	 * @return The builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builds runtime options.
	 */
	public static final class Builder {
		/**
		 * Creates a builder with no runtime memory limit.
		 */
		public Builder() {}

		private long maxMemory;

		/**
		 * Sets the maximum size of each memory in pages.
		 * 
		 * @param maxMemory
		 *            The maximum page count, or zero for no runtime limit.
		 * @return This builder.
		 * @throws IllegalArgumentException
		 *             If the limit is negative.
		 */
		public Builder maxMemory(long maxMemory) {
			if(maxMemory < 0) {
				throw new IllegalArgumentException("maximum memory size must be nonnegative");
			}
			this.maxMemory = maxMemory;
			return this;
		}

		/**
		 * Creates immutable options from the current builder settings.
		 * 
		 * @return The options.
		 */
		public RuntimeOptions build() {
			return new RuntimeOptions(maxMemory);
		}
	}
}
