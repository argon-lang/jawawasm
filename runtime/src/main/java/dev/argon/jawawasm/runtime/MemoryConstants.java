package dev.argon.jawawasm.runtime;

import java.lang.foreign.ValueLayout;

import static java.nio.ByteOrder.LITTLE_ENDIAN;

final class MemoryConstants {
	private MemoryConstants() {}
	public static final int PAGE_SIZE = 65536;
	public static final int MAX_PAGES_32 = (int)((1L << 32) / PAGE_SIZE);
	public static final long MAX_PAGES_64 = Long.divideUnsigned(-1L, PAGE_SIZE) + 1;

	public static final ValueLayout.OfByte I8_LAYOUT = ValueLayout.JAVA_BYTE.withOrder(LITTLE_ENDIAN)
		.withByteAlignment(1);
	public static final ValueLayout.OfShort I16_LAYOUT = ValueLayout.JAVA_SHORT.withOrder(LITTLE_ENDIAN)
		.withByteAlignment(1);
	public static final ValueLayout.OfInt I32_LAYOUT = ValueLayout.JAVA_INT.withOrder(LITTLE_ENDIAN)
		.withByteAlignment(1);
	public static final ValueLayout.OfLong I64_LAYOUT = ValueLayout.JAVA_LONG.withOrder(LITTLE_ENDIAN)
		.withByteAlignment(1);
	public static final ValueLayout.OfFloat F32_LAYOUT = ValueLayout.JAVA_FLOAT.withOrder(LITTLE_ENDIAN)
		.withByteAlignment(1);
	public static final ValueLayout.OfDouble F64_LAYOUT = ValueLayout.JAVA_DOUBLE.withOrder(LITTLE_ENDIAN)
		.withByteAlignment(1);
}
