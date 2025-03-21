package dev.argon.jawawasm.engine.classloader;

import dev.argon.jawawasm.engine.ModuleResolver;
import dev.argon.jawawasm.engine.compiler.CompilerOptions;
import dev.argon.jawawasm.engine.compiler.ModuleCompiler;
import dev.argon.jawawasm.engine.compiler.ReflectionModuleLoader;
import dev.argon.jawawasm.engine.compiler.WasmModuleRealization;
import dev.argon.jawawasm.format.ModuleFormatException;
import dev.argon.jawawasm.format.modules.Module;
import dev.argon.jawawasm.runtime.*;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * WebAssembly engine using classloaders.
 */
public class ClassLoaderEngine {
	/**
	 * Create an engine.
	 * @param packageName The name of the Java package used for generated classes.
	 * @param context The runtime context.
	 */
	public ClassLoaderEngine(String packageName, RuntimeContext context) {
		this.context = context;

		classFile = ClassFile.of(
			ClassFile.ShortJumpsOption.FIX_SHORT_JUMPS,
			ClassFile.ClassHierarchyResolverOption.of(
				new LoaderHierarchyResolver().orElse(ClassHierarchyResolver.defaultResolver())
			)
		);

		compiler = new ModuleCompiler(new CompilerOptions(
			classFile,
			packageName
		));
	}

	private final RuntimeContext context;
	private final ClassFile classFile;
	private final ModuleCompiler compiler;
	private final ClassLoader classLoader = new EngineClassLoaderImpl();
	private final AtomicInteger moduleIndex = new AtomicInteger(0);

	private final Map<String, byte[]> generatedClasses = new ConcurrentHashMap<>();
	private final Map<WasmModule, WasmModuleRealization> instanceToRealization = new ConcurrentHashMap<>();

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
			var classRealization = compiler.enqueueModule(module, "Module" + currentIndex, new MappedResolver(resolver));
			List<byte[]> newClassBytecode = new ArrayList<>();

			for(;;) {
				var cg = compiler.dequeueGenerator();

				if(cg == null) {
					break;
				}

				var content = cg.generate();

				var binaryName = getBinaryName(cg.className());

				generatedClasses.put(binaryName, content);
				newClassBytecode.add(content);
			}

			for(var ncb : newClassBytecode) {
				var errors = classFile.verify(ncb);

				if(!errors.isEmpty()) {
					System.err.println("Bytecode: " + Base64.getEncoder().encodeToString(ncb));
					var classModel = classFile.parse(ncb);
					System.err.println(classModel);
					for(var method : classModel.methods()) {
						System.err.println(method);
						method.code().ifPresent(code -> code.elementList().forEach(System.err::println));

					}

					for(var e : errors) {
						e.printStackTrace();
					}

					throw new RuntimeException("Verification errors for " + classModel.thisClass() + ":\n" + errors.stream().map(VerifyError::toString).collect(Collectors.joining(",")));
				}
			}

			Class<?> cls = classLoader.loadClass(getBinaryName(classRealization.classDesc()));

			List<Class<?>> methodParamTypes = new ArrayList<>();
			List<Object> methodArgs = new ArrayList<>();

			methodParamTypes.add(RuntimeContext.class);
			methodArgs.add(context);


			Set<String> seenImportModules = new HashSet<>();
			for(var imp : module.imports()) {
				if(!seenImportModules.add(imp.module())) {
					continue;
				}

				var impModule = resolver.resolve(imp.module());
				methodParamTypes.add(impModule.getClass());
				methodArgs.add(impModule);
			}

			WasmModule instance;
			try {
				instance = (WasmModule)cls.getDeclaredConstructor(methodParamTypes.toArray(Class<?>[]::new)).newInstance(methodArgs.toArray());
			}
			catch(InvocationTargetException e) {
				throw new ExecutionException(e.getCause());
			}

			instanceToRealization.put(instance, classRealization);

			return instance;
		}
		catch(ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException e) {
			throw new ModuleLinkException(e);
		}
	}

	/**
	 * Adds a host module to this engine.
	 * @param module The module to add.
	 * @throws ModuleFormatException if the module is invalid
	 */
	public void addHostModule(WasmModule module) throws ModuleFormatException {
		var realization = new ReflectionModuleLoader(compiler).loadModule(module.getClass());
		instanceToRealization.put(module, realization);
	}

	private static String getBinaryName(ClassDesc classDesc) {
		var desc = classDesc.descriptorString();
		return desc.substring(1, desc.length() - 1).replace('/', '.');
	}

	private final class MappedResolver implements ModuleResolver<WasmModuleRealization> {
		public MappedResolver(ModuleResolver<WasmModule> resolver) {
			this.resolver = resolver;
		}

		private final ModuleResolver<WasmModule> resolver;

		@Override
		public WasmModuleRealization resolve(String name) throws ModuleResolutionException {
			WasmModule resolvedModule = resolver.resolve(name);

			var resolved = instanceToRealization.get(resolvedModule);
			if(resolved == null) {
				throw new ModuleResolutionException("Could not find resolved module realization");
			}

			return resolved;
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

	private final class LoaderHierarchyResolver implements ClassHierarchyResolver {
		@Override
		public ClassHierarchyInfo getClassInfo(ClassDesc classDesc) {
			var binaryName = getBinaryName(classDesc);
			var bytecode = generatedClasses.get(binaryName);

			if(bytecode != null) {
				var classModel = classFile.parse(bytecode);
				if(classModel.flags().has(AccessFlag.INTERFACE)) {
					return ClassHierarchyInfo.ofInterface();
				}
				else {
					var superclass = classModel.superclass().orElse(null);
					if(superclass == null) {
						return ClassHierarchyInfo.ofClass(null);
					}

					return ClassHierarchyInfo.ofClass(ClassDesc.ofInternalName(superclass.asInternalName()));
				}
			}

			// Not a generated class, fallback to reflection.
			Class<?> cls;
			try {
				cls = Class.forName(binaryName);

			} catch(ClassNotFoundException e) {
				throw new RuntimeException(e);
			}

			if(cls.isInterface()) {
				return ClassHierarchyInfo.ofInterface();
			}
			else {
				var superclass = cls.getSuperclass();
				if(superclass == null) {
					return ClassHierarchyInfo.ofClass(null);
				}
				else {
					return ClassHierarchyInfo.ofClass(ClassDesc.ofDescriptor(superclass.descriptorString()));
				}
			}
		}
	}

}
