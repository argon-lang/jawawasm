package dev.argon.jawawasm.engine.classloader;

import dev.argon.jawawasm.engine.ModuleResolver;
import dev.argon.jawawasm.engine.compiler.CompilerOptions;
import dev.argon.jawawasm.engine.compiler.ModuleClassGenerator;
import dev.argon.jawawasm.engine.compiler.ModuleCompiler;
import dev.argon.jawawasm.engine.compiler.WasmClassGenerator;
import dev.argon.jawawasm.format.modules.Module;
import dev.argon.jawawasm.runtime.MemoryAllocator;
import dev.argon.jawawasm.runtime.ModuleLinkException;
import dev.argon.jawawasm.runtime.ModuleResolutionException;
import dev.argon.jawawasm.runtime.WasmModule;

import java.lang.classfile.ClassFile;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public class ClassLoaderEngine {
	/**
	 * Create an engine.
	 */
	public ClassLoaderEngine(String packageName, MemoryAllocator allocator) {
		this.packageName = packageName;
		this.allocator = allocator;
		compiler = new ModuleCompiler(new CompilerOptions(
			packageName
		));
	}

	private final String packageName;
	private final MemoryAllocator allocator;
	private final ModuleCompiler compiler;
	private final ClassLoader classLoader = new EngineClassLoaderImpl();
	private final AtomicInteger moduleIndex = new AtomicInteger(0);

	private final Map<String, byte[]> generatedClasses = new ConcurrentHashMap<>();
	private final Map<WasmModule, ModuleClassGenerator> instanceToGenerator = new ConcurrentHashMap<>();
	private final Map<ModuleClassGenerator, WasmModule> generatorToInstance = new ConcurrentHashMap<>();

	/**
	 * Instantiates a WebAssembly module.
	 * @param module The module to instantiate.
	 * @param resolver The resolver to use.
	 * @return The instantiated module.
	 * @throws ExecutionException when an error occurs executing WebAssembly code.
	 * @throws ModuleLinkException when an error occurs while linking.
	 */
	public WasmModule instantiateModule(Module module, ModuleResolver<WasmModule> resolver) throws ExecutionException, ModuleLinkException {
		try {
			int currentIndex = moduleIndex.getAndIncrement();
			var classGenerator = compiler.enqueueModule(module, "Module" + currentIndex, new MappedResolver(resolver));
			for(;;) {
				var cg = compiler.dequeueGenerator();

				if(cg == null) {
					break;
				}

				var content = cg.generate();

				var binaryName = getBinaryName(cg);

				generatedClasses.put(binaryName, content);
			}

			Class<?> cls = classLoader.loadClass(getBinaryName(classGenerator));

			WasmModule instance;
			try {
				instance = (WasmModule)cls.getDeclaredConstructor().newInstance();
			}
			catch(InvocationTargetException e) {
				throw new ExecutionException(e.getCause());
			}

			instanceToGenerator.put(instance, classGenerator);
			generatorToInstance.put(classGenerator, instance);

			return instance;
		}
		catch(InterruptedException | ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException e) {
			throw new ModuleLinkException(e);
		}
	}

	private static String getBinaryName(WasmClassGenerator cg) {
		var desc = cg.className().descriptorString();
		return desc.substring(1, desc.length() - 1).replace('/', '.');
	}

	private final class MappedResolver implements ModuleResolver<ModuleClassGenerator> {
		public MappedResolver(ModuleResolver<WasmModule> resolver) {
			this.resolver = resolver;
		}

		private final ModuleResolver<WasmModule> resolver;

		@Override
		public ModuleClassGenerator resolve(ModuleClassGenerator importer, String name) throws ModuleResolutionException {
			var importer2 = generatorToInstance.get(importer);
			if(importer2 == null) {
				throw new ModuleResolutionException("Could not find importer module");
			}

			WasmModule resolvedModule = resolver.resolve(importer2, name);
			var resolvedModule2 = instanceToGenerator.get(resolvedModule);
			if(resolvedModule2 == null) {
				throw new ModuleResolutionException("Could not map module instance to generator");
			}

			return resolvedModule2;
		}
	}

//	private final class EngineModuleFinder extends ModuleFinder {
//		@Override
//		public Optional<ModuleReference> find(String name) {
//			if(!name.equals(packageName)) {
//				return Optional.empty();
//			}
//
//			var descriptor = ModuleDescriptor.newModule(name)
//				.requires("java.base")
//				.exports(packageName)
//				.build();
//
//			return Optional.of();
//		}
//
//		@Override
//		public Set<ModuleReference> findAll() {
//			return Set.of();
//		}
//	}
//
//	private final class EngineModuleReference extends ModuleReference {
//
//	}

	private final class EngineClassLoaderImpl extends ClassLoader {
		private final Map<String, Class<?>> loadedClasses = new ConcurrentHashMap<>();

		@Override
		protected Class<?> findClass(String name) throws ClassNotFoundException {
			byte[] data = generatedClasses.get(name);
			if(data == null) {
				throw new ClassNotFoundException();
			}

			return defineClass(name, data, 0, data.length);
		}
	}

}
