package dev.argon.jawawasm.format.generator;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import dev.argon.jawawasm.engine.aot.AOTEngine;
import dev.argon.jawawasm.engine.validator.ModuleValidator;
import dev.argon.jawawasm.format.binary.ModuleReader;

import java.io.File;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;

public final class GenerateTextParser {

	private GenerateTextParser() {}

	public static void main(String[] args) throws Exception {
		var output = Path.of(args[1]);
		if(Files.exists(output)) {
			try(var paths = Files.walk(output)) {
				for(var path : paths.sorted(Comparator.reverseOrder()).toList()) {
					Files.delete(path);
				}
			}
		}
		var classPath = ImmutableList.<Path>builder();
		for(var entry : Splitter.on(File.pathSeparatorChar)
			.split(Objects.requireNonNull(System.getProperty("java.class.path")))) {
			var path = Path.of(entry);
			if(Files.exists(path)) {
				classPath.add(path);
			}
		}
		var engine = new AOTEngine("dev.argon.jawawasm.format.internal.generated", classPath.build(), output);
		var callbacks = engine.addHostModule(ClassDesc.of("dev.argon.jawawasm.format.internal.ParserCallbacks"));
		try(var input = Files.newInputStream(Path.of(args[0]))) {
			var module = new ModuleReader(input).readModule();
			ModuleValidator.validateModule(module);
			engine.emitModule(module, "TextParserModule", name -> {
				if(!name.equals("callback")) {
					throw new dev.argon.jawawasm.runtime.ModuleResolutionException("Unknown module: " + name);
				}
				return callbacks;
			});
		}
	}
}
