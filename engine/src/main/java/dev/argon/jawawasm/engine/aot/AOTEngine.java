package dev.argon.jawawasm.engine.aot;

import com.google.common.collect.ImmutableList;
import dev.argon.jawawasm.engine.ModuleResolver;
import dev.argon.jawawasm.engine.compiler.*;
import dev.argon.jawawasm.format.ModuleFormatException;
import dev.argon.jawawasm.format.modules.Module;
import dev.argon.jawawasm.format.types.*;
import dev.argon.jawawasm.runtime.*;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.constant.ClassDesc;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebAssembly engine using reflection and runtime class loading.
 */
public class AOTEngine {
	/**
	 * Create an engine.
	 * 
	 * @param packageName
	 *            The name of the Java package used for generated classes.
	 * @param classPath
	 *            The classpath to use for loading classes.
	 * @param outputDir
	 *            The directory to write generated classes to.
	 */
	public AOTEngine(String packageName, ImmutableList<Path> classPath, Path outputDir) {
		this.classPath = classPath;
		this.outputDir = outputDir;

		compiler = new ModuleCompiler(
			new CompilerOptions(
				new GeneratedClassResolver().orElse(new ClassPathResolver())
					.orElse(ClassHierarchyResolver.defaultResolver()),
				packageName
			)
		);

		try {
			moduleLoader = new AOTModuleLoader(compiler, classPath);
			moduleLoader.loadResultClass(ClassDesc.ofDescriptor(ResultVoid.class.descriptorString()));
			moduleLoader.loadResultClass(ClassDesc.ofDescriptor(ResultI32.class.descriptorString()));
			moduleLoader.loadResultClass(ClassDesc.ofDescriptor(ResultI64.class.descriptorString()));
			moduleLoader.loadResultClass(ClassDesc.ofDescriptor(ResultF32.class.descriptorString()));
			moduleLoader.loadResultClass(ClassDesc.ofDescriptor(ResultF64.class.descriptorString()));
			moduleLoader.loadResultClass(ClassDesc.ofDescriptor(ResultRef.class.descriptorString()));
		}
		catch(ModuleFormatException e) {
			throw new RuntimeException("Internal error: Runtime library contains invalid type.", e);
		}
	}

	private final ModuleCompiler compiler;
	private final AOTModuleLoader moduleLoader;
	private final ImmutableList<Path> classPath;
	private final Path outputDir;

	private final Map<ClassDesc, ClassHierarchyResolver.ClassHierarchyInfo> generatedClassHierarchies = new ConcurrentHashMap<>();

	/**
	 * Instantiates a WebAssembly module.
	 * 
	 * @param module
	 *            The module to instantiate.
	 * @param className
	 *            The name of the class to generate, or {@code null} to use the
	 *            sanitized module name from the name section.
	 * @param resolver
	 *            Resolves this module's imports.
	 * @return The compiled module realization.
	 * @throws IOException
	 *             when an error occurs while writing output.
	 * @throws IllegalArgumentException
	 *             if no class name or nonempty module name is provided.
	 */
	public WasmModuleRealization emitModule(
		Module module,
		@Nullable String className,
		ModuleResolver<WasmModuleRealization> resolver
	) throws IOException {
		WasmModuleRealization realization = compiler.enqueueModule(module, className, resolver);

		genLoop : for(;;) {
			var gen = compiler.dequeueGenerator();

			String name;
			byte[] content;

			switch(gen) {
				case null -> {
					break genLoop;
				}
				case WasmClassGenerator cg -> {
					generatedClassHierarchies.put(cg.className(), cg.hierarchyInfo());
					content = cg.generate();
					name = getInternalName(cg.className()) + ".class";
				}
				case WasmResourceGenerator rg -> {
					content = rg.generate();
					name = rg.resourceName();
				}
			}

			var outputPath = outputDir.resolve(name);
			var parent = outputPath.getParent();
			if(parent != null) {
				Files.createDirectories(parent);
			}
			Files.write(outputPath, content);
		}
		return realization;
	}

	/**
	 * Adds a host module to this engine.
	 * 
	 * @param moduleClass
	 *            The module class to add.
	 * @return The host module realization.
	 * @throws ModuleFormatException
	 *             if the module is invalid
	 */
	public WasmModuleRealization addHostModule(ClassDesc moduleClass) throws ModuleFormatException {
		return moduleLoader.loadModule(moduleClass);
	}

	private static String getInternalName(ClassDesc classDesc) {
		var desc = classDesc.descriptorString();
		return desc.substring(1, desc.length() - 1);
	}

	private final class GeneratedClassResolver implements ClassHierarchyResolver {
		@Override
		public @Nullable ClassHierarchyInfo getClassInfo(ClassDesc classDesc) {
			return generatedClassHierarchies.get(classDesc);
		}
	}

	private final class ClassPathResolver implements ClassHierarchyResolver {
		@Override
		public @Nullable ClassHierarchyInfo getClassInfo(ClassDesc classDesc) {
			ClassModel model;
			try {
				model = AOTClasspathLookup.loadClassModel(compiler.classFile(), classPath, classDesc);
			}
			catch(IOException e) {
				throw new UncheckedIOException(e);
			}

			if(model == null) {
				return null;
			}

			return ClassHierarchyInfo.ofClass(model.superclass().map(ClassEntry::asSymbol).orElse(null));
		}
	}

}
