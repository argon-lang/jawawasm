package dev.argon.jawawasm.app.wast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.lang.ProcessBuilder;
import java.lang.Process;

public class WastLoader {
	public WastLoader(Path wasmToolsExecutable) {
		this.wasmToolsExecutable = wasmToolsExecutable;
	}
	
	private final Path wasmToolsExecutable;

	public WastScriptLoaded loadScript(Path scriptPath) throws IOException, InterruptedException {
		Path dir = Files.createTempDirectory("jawawasm-");
		WastScriptLoaded loaded = null;
		try {
			ProcessBuilder pb = new ProcessBuilder(wasmToolsExecutable.toString(), "json-from-wast", scriptPath.toString(), "--wasm-dir", dir.toString());
			pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
			pb.redirectError(ProcessBuilder.Redirect.PIPE);

			Process process = pb.start();

			ObjectMapper mapper = new ObjectMapper();
			mapper.registerModule(new GuavaModule());
			String jsonText = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);

			int exitCode = process.waitFor();
			if(exitCode != 0) {
				String errorText = IOUtils.toString(process.getErrorStream(), StandardCharsets.UTF_8);
				throw new IOException("wasm-tools failed with exit code " + exitCode + "\nOutput: " + jsonText + "\nError: " + errorText);
			}

			WastScript script = mapper.readValue(jsonText, WastScript.class);

			loaded = new WastScriptLoaded(dir, script);
		}
		finally {
			if(loaded == null) {
				try { FileUtils.deleteDirectory(dir.toFile()); }
				catch(IOException _) {
					// Unable to cleanup temp files. Don't suppress original error.
				}
			}
		}
		return loaded;
	}

	public byte[] loadModule(Path file, ModuleType moduleType) throws IOException, InterruptedException {
		return switch(moduleType) {
			case BINARY -> Files.readAllBytes(file);
			case TEXT -> {
				throw new RuntimeException("Unexpected text format module");
//				ProcessBuilder pb = new ProcessBuilder(wasmToolsExecutable.toString(), "parse", file.toString());
//				pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
//				Process process = pb.start();
//				byte[] data = process.getInputStream().readAllBytes();
//				int exitCode = process.waitFor();
//				if(exitCode != 0) {
//					throw new IOException("wasm-tools failed with exit code " + exitCode);
//				}
//
//				yield data;
			}
		};
	}

}
