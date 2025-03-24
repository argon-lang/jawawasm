package dev.argon.jawawasm.engine.reflection;

import dev.argon.jawawasm.engine.ModuleResolver;
import dev.argon.jawawasm.engine.compiler.*;
import dev.argon.jawawasm.format.ModuleFormatException;
import dev.argon.jawawasm.format.modules.Module;
import dev.argon.jawawasm.format.types.*;
import dev.argon.jawawasm.runtime.*;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.lang.constant.ConstantDescs.*;

/**
 * WebAssembly engine using reflection and runtime class loading.
 */
public class ReflectionEngine {
	/**
	 * Create an engine.
	 * @param packageName The name of the Java package used for generated classes.
	 * @param context The runtime context.
	 */
	public ReflectionEngine(String packageName, RuntimeContext context) {
		this.context = context;

		compiler = new ModuleCompiler(new CompilerOptions(
			ClassHierarchyResolver.defaultResolver(),
			packageName
		));
	}

	private final RuntimeContext context;
	private final ModuleCompiler compiler;
	private final ClassLoader classLoader = new EngineClassLoaderImpl();
	private final AtomicInteger moduleIndex = new AtomicInteger(0);

	private final Map<String, byte[]> generatedFiles = new ConcurrentHashMap<>();
	private final Map<WasmModule, WasmModuleRealization> instanceToRealization = new ConcurrentHashMap<>();

	/**
	 * Instantiates a WebAssembly module.
	 * @param module The module to instantiate.
	 * @param resolver The resolver to use.
	 * @return The instantiated module.
	 * @throws ExecutionException when an error occurs executing WebAssembly code.
	 * @throws ModuleLinkException when an error occurs while linking.
	 */
	public ReflectionModule instantiateModule(Module module, ModuleResolver<ReflectionModule> resolver) throws ExecutionException, ModuleLinkException {
		try {
			int currentIndex = moduleIndex.getAndIncrement();
			WasmModuleRealization classRealization = compiler.enqueueModule(module, "Module" + currentIndex, new MappedResolver(resolver));
			List<byte[]> newClassBytecode = new ArrayList<>();

			genLoop:
			for(;;) {
				var gen = compiler.dequeueGenerator();

				String name;
				byte[] content;

				switch(gen) {
					case null -> {
						break genLoop;
					}
					case WasmClassGenerator cg -> {
						content = cg.generate();
						name = getInternalName(cg.className()) + ".class";
						newClassBytecode.add(content);
					}
					case WasmResourceGenerator rg -> {
						content = rg.generate();
						name = rg.resourceName();
					}
				}

				generatedFiles.put(name, content);
			}

			for(var ncb : newClassBytecode) {
				var errors = compiler.classFile().verify(ncb);

				if(!errors.isEmpty()) {
					System.err.println("Bytecode: " + Base64.getEncoder().encodeToString(ncb));
					var classModel = compiler.classFile().parse(ncb);
					System.err.println(classModel);
					for(var method : classModel.methods()) {
						System.err.println(method);
						method.code().ifPresent(code -> code.elementList().forEach(System.err::println));
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

				var impModule = resolver.resolve(imp.module()).module();
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

			return new ReflectionModule(instance, getExports(cls, classRealization));
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
	public ReflectionModule addHostModule(WasmModule module) throws ModuleFormatException, ModuleLinkException {
		var cls = module.getClass();
		var realization = new ReflectionModuleLoader(compiler).loadModule(cls);
		instanceToRealization.put(module, realization);
		Map<String, ReflectionExport> exports;
		try {
			exports = getExports(cls, realization);
		}
		catch(ClassNotFoundException | NoSuchMethodException e) {
			throw new ModuleLinkException(e);
		}

		return new ReflectionModule(module, exports);
	}

	private Map<String, ReflectionExport> getExports(Class<?> cls, WasmModuleRealization classRealization) throws ClassNotFoundException, NoSuchMethodException {
		Map<String, ReflectionExport> exports = new HashMap<>();
		for(var exp : classRealization.exports()) {
			ReflectionExport export;
			switch(exp) {
				case WasmExportRealization.OfInstanceMethod expMethod -> {
					switch(expMethod.externalType()) {
						case DefType defType -> {
							Class<?>[] paramTypes = new Class<?>[expMethod.methodType().parameterCount()];
							for(int i = 0; i < expMethod.methodType().parameterCount(); ++i) {
								paramTypes[i] = classDescToClass(expMethod.methodType().parameterType(i));
							}
							var method = cls.getMethod(expMethod.methodName(), paramTypes);
							export = new ReflectionExport.FunctionExport(method);
						}
						case GlobalType globalType -> {
							var method = cls.getMethod(expMethod.methodName());
							export = switch(globalType.mutability()) {
								case Const -> new ReflectionExport.GlobalExportConst(method);
								case Var -> new ReflectionExport.GlobalExportVar(method);
							};
						}
//						case MemType memType -> throw new RuntimeException("Not implemented");
//						case TableType tableType -> throw new RuntimeException("Not implemented");
//						case TagType tagType -> throw new RuntimeException("Not implemented");
						default -> {
							continue;
						}
					}
				}
				case WasmExportRealization.OfInnerClass _ -> {
					continue;
				}
			}

			exports.put(exp.exportName(), export);
		}

		return exports;
	}

	private Class<?> classDescToClass(ClassDesc classDesc) throws ClassNotFoundException {
		if(classDesc == CD_byte) {
			return byte.class;
		}
		else if(classDesc == CD_short) {
			return short.class;
		}
		else if(classDesc == CD_int) {
			return int.class;
		}
		else if(classDesc == CD_long) {
			return long.class;
		}
		else if(classDesc == CD_float) {
			return float.class;
		}
		else if(classDesc == CD_double) {
			return double.class;
		}
		else if(classDesc == CD_char) {
			return char.class;
		}
		else if(classDesc == CD_boolean) {
			return boolean.class;
		}
		else if(classDesc == CD_void) {
			return void.class;
		}
		else if(classDesc.isArray()) {
			return classDescToClass(classDesc.componentType()).arrayType();
		}
		else {
			return classLoader.loadClass(getBinaryName(classDesc));
		}
	}



	private static String getBinaryName(ClassDesc classDesc) {
		return getInternalName(classDesc).replace('/', '.');
	}

	private static String getInternalName(ClassDesc classDesc) {
		var desc = classDesc.descriptorString();
		return desc.substring(1, desc.length() - 1);
	}

	private final class MappedResolver implements ModuleResolver<WasmModuleRealization> {
		public MappedResolver(ModuleResolver<ReflectionModule> resolver) {
			this.resolver = resolver;
		}

		private final ModuleResolver<ReflectionModule> resolver;

		@Override
		public WasmModuleRealization resolve(String name) throws ModuleResolutionException {
			WasmModule resolvedModule = resolver.resolve(name).module();

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
		@Override
		protected Class<?> findClass(String name) throws ClassNotFoundException {
			byte[] data = generatedFiles.get(name.replace('.', '/') + ".class");
			if(data == null) {
				throw new ClassNotFoundException();
			}

			return defineClass(name, data, 0, data.length);
		}

		@Override
		protected @Nullable URL findResource(String name) {
			if(generatedFiles.containsKey(name)) {
				var uri = URI.create("memory:///" + name);
				try {
					return URL.of(uri, new EngineResourceURLStreamLoader());
				} catch(MalformedURLException e) {
					return null;
				}
			}
			else {
				return null;
			}
		}
	}

	private final class EngineResourceURLStreamLoader extends URLStreamHandler {
		@Override
		protected URLConnection openConnection(URL url) throws IOException {
			return new EngineResourceURLConnection(url);
		}
	}

	private class EngineResourceURLConnection extends URLConnection {
		public EngineResourceURLConnection(URL url) {
			super(url);
		}

		@Override
		public void connect() throws IOException {
		}

		@Override
		public InputStream getInputStream() throws IOException {
			var path = getURL().getPath();
			if(path.startsWith("/")) path = path.substring(1);
			var data = generatedFiles.get(path);
			Objects.requireNonNull(data);
			return new ByteArrayInputStream(data);
		}
	}

}
