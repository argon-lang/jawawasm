package dev.argon.jawawasm.app.wast;

import org.apache.commons.io.FileUtils;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;

public class WastScriptLoaded implements Closeable {
	WastScriptLoaded(Path dir, WastScript script) {
		this.dir = dir;
		this.script = script;
	}


	private final Path dir;
	private final WastScript script;

	public Path getDir() {
		return dir;
	}

	public WastScript getScript() {
		return script;
	}

	@Override
	public void close() throws IOException {
		FileUtils.deleteDirectory(dir.toFile());
	}
}
