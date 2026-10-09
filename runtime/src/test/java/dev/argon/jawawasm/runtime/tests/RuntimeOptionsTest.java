package dev.argon.jawawasm.runtime.tests;

import dev.argon.jawawasm.runtime.RuntimeOptions;
import dev.argon.jawawasm.runtime.WasmMemory32;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RuntimeOptionsTest {
	@Test
	public void defaultsToUnlimitedMemory() {
		assertEquals(0, new RuntimeOptions().maxMemory());
		assertEquals(0, RuntimeOptions.builder().build().maxMemory());
	}

	@Test
	public void builderProducesIndependentSnapshots() {
		var builder = new RuntimeOptions.Builder().maxMemory(2);
		var first = builder.build();
		var second = builder.maxMemory(3).build();
		assertEquals(2, first.maxMemory());
		assertEquals(3, second.maxMemory());
		assertEquals(0, builder.maxMemory(0).build().maxMemory());
		assertEquals(Long.MAX_VALUE, builder.maxMemory(Long.MAX_VALUE).build().maxMemory());
		assertThrows(IllegalArgumentException.class, () -> builder.maxMemory(-1));
		assertEquals(Long.MAX_VALUE, builder.build().maxMemory());
	}

	@Test
	public void memoryHonorsRuntimeLimitDuringCreationAndGrowth() {
		var options = RuntimeOptions.builder().maxMemory(2).build();
		assertThrows(IllegalArgumentException.class, () -> WasmMemory32.create(options, 3, null));
		var memory = WasmMemory32.create(options, 1, null);
		assertEquals(1, memory.grow(1));
		assertEquals(-1, memory.grow(1));
		assertEquals(2, memory.pageSize());
	}
}
