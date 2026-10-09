package dev.argon.jawawasm.runtime.tests;

import dev.argon.jawawasm.runtime.Util;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class UtilReadDataTest {
	@Test
	public void readsAllBytesIncludingEmptyAndMultipleBufferLengths() {
		for(int size : new int[]{0, 1, 4096, 20000}) {
			byte[] expected = new byte[size];
			new Random(size).nextBytes(expected);
			assertArrayEquals(expected, Util.readData(new ByteArrayInputStream(expected)));
		}
	}

	@Test
	public void preservesIOExceptionCause() {
		IOException cause = new IOException("read failure");
		InputStream stream = new InputStream() {
			@Override
			public int read() throws IOException {
				throw cause;
			}

			@Override
			public int read(byte[] b, int off, int len) throws IOException {
				throw cause;
			}
		};
		assertSame(cause, assertThrows(UncheckedIOException.class, () -> Util.readData(stream)).getCause());
	}

	@Test
	public void leavesTheCallerStreamOpen() {
		TrackingStream stream = new TrackingStream();
		assertArrayEquals(new byte[]{1, 2, 3}, Util.readData(stream));
		assertFalse(stream.closed);
	}

	private static final class TrackingStream extends ByteArrayInputStream {
		private boolean closed;

		private TrackingStream() {
			super(new byte[]{1, 2, 3});
		}

		@Override
		public void close() throws IOException {
			closed = true;
			super.close();
		}
	}
}
