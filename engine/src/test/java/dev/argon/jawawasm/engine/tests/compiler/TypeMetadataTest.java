package dev.argon.jawawasm.engine.tests.compiler;

import com.google.common.collect.ImmutableList;
import dev.argon.jawawasm.engine.aot.AOTEngine;
import dev.argon.jawawasm.engine.compiler.*;
import dev.argon.jawawasm.engine.reflection.*;
import dev.argon.jawawasm.format.ModuleFormatException;
import dev.argon.jawawasm.runtime.*;
import org.junit.jupiter.api.Test;
import java.lang.classfile.*;
import java.lang.constant.ClassDesc;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import static org.junit.jupiter.api.Assertions.*;

public class TypeMetadataTest extends CompilerTestBase {
	private static final RuntimeContext CONTEXT = RuntimeOptions::new;
	private ImmutableList<Path> classPath(Path generated) throws Exception {
		return ImmutableList.of(
			generated,
			Path.of(
				Objects.requireNonNull(
					Objects.requireNonNull(ResultVoid.class.getProtectionDomain().getCodeSource()).getLocation()
				).toURI()
			)
		);
	}

	@Test
	public void producerConsumerThroughBothLoaders() throws Exception {
		var producer = readWatFile(Path.of("src/test/resources/compiler/metadata-producer.wat"));
		var consumer = readWatFile(Path.of("src/test/resources/compiler/metadata-consumer.wat"));
		var output = temporaryDirectory.resolve("producer");
		Files.createDirectories(output);
		var engine = new AOTEngine("metadata.producer", classPath(output), output);
		var original = engine.emitModule(producer, "Producer", name -> {
			throw new AssertionError(name);
		});
		// Bindings must be discovered from annotations, even when the JVM names differ.
		try(var files = Files.walk(output)) {
			for(var file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
				var cf = ClassFile.of();
				var model = cf.parse(file);
				if(!model.thisClass().asInternalName().contains("/Array"))
					continue;
				Files.write(file, cf.transformClass(model, (clb, element) -> {
					if(element instanceof MethodModel method
						&& (method.methodName().equalsString("get") || method.methodName().equalsString("set"))) {
						String renamed = method.methodName().equalsString("get") ? "readValue" : "writeValue";
						clb.withMethod(
							renamed,
							method.methodTypeSymbol(),
							method.flags().flagsMask(),
							mb -> method.forEach(mb::with)
						);
					}
					else
						clb.with(element);
				}));
			}
		}
		try(var loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, getClass().getClassLoader())) {
			var moduleClass = loader.loadClass("metadata.producer.Producer");
			var instance = (WasmModule)moduleClass.getConstructor(RuntimeContext.class).newInstance(CONTEXT);
			var reflection = new ReflectionEngine("metadata.reflection", CONTEXT);
			var loaded = reflection.addHostModule(instance);
			assertEquals(original.exports().size(), loaded.exports().size());
			assertTrue(loaded.exports().containsKey(""));
			assertSame(
				((ReflectionExport.TagExport)Objects.requireNonNull(loaded.exports().get("tag"))).tagClass(),
				((ReflectionExport.TagExport)Objects.requireNonNull(loaded.exports().get("tag-alias"))).tagClass()
			);
			var result = reflection.instantiateModule(consumer, name -> loaded);
			var run = ((ReflectionExport.FunctionExport)Objects.requireNonNull(result.exports().get("run"))).method();
			assertEquals(45, ResultI32.get((ResultI32)Objects.requireNonNull(run.invoke(result.module()))).item0);
			assertSame(
				((ReflectionExport.TagExport)Objects.requireNonNull(loaded.exports().get("tag"))).tagClass(),
				((ReflectionExport.TagExport)Objects.requireNonNull(result.exports().get("tag-reexport"))).tagClass()
			);
			for(boolean jar : List.of(false, true)) {
				Path dependency = output;
				if(jar) {
					dependency = temporaryDirectory.resolve("producer.jar");
					try(var stream = new JarOutputStream(Files.newOutputStream(dependency));
						var files = Files.walk(output)) {
						for(var file : files.filter(Files::isRegularFile).toList()) {
							stream.putNextEntry(new JarEntry(output.relativize(file).toString()));
							Files.copy(file, stream);
							stream.closeEntry();
						}
					}
				}
				var consumerOutput = temporaryDirectory.resolve(jar ? "jar-consumer" : "dir-consumer");
				var fresh = new AOTEngine("metadata.consumer", classPath(dependency), consumerOutput);
				var imported = fresh.addHostModule(original.classDesc());
				assertEquals(original.exports().size(), imported.exports().size());
				for(var expected : original.exports()) {
					var actual = imported.exports().stream().filter(e -> e.exportName().equals(expected.exportName()))
						.findFirst().orElseThrow();
					if(expected instanceof WasmExportRealization.OfInnerClass tag) {
						var loadedTag = (WasmExportRealization.OfInnerClass)actual;
						assertEquals(tag.classDesc(), loadedTag.classDesc());
						assertEquals(tag.constructorType(), loadedTag.constructorType());
						assertEquals(tag.tagDefType(), loadedTag.tagDefType());
					}
					else
						assertEquals(expected, actual);
				}
				fresh.emitModule(consumer, "Consumer", name -> imported);
				try(var consumerLoader = new URLClassLoader(
					new java.net.URL[]{consumerOutput.toUri().toURL()},
					loader
				)) {
					var c = consumerLoader.loadClass("metadata.consumer.Consumer");
					var object = c.getConstructor(RuntimeContext.class, moduleClass).newInstance(CONTEXT, instance);
					assertEquals(
						45,
						ResultI32.get((ResultI32)Objects.requireNonNull(c.getMethod("run").invoke(object))).item0
					);
					var reloaded = new AOTEngine(
						"metadata.third",
						ImmutableList.<Path>builder().add(consumerOutput).addAll(classPath(dependency)).build(),
						temporaryDirectory.resolve("unused")
					);
					var reexported = reloaded.addHostModule(ClassDesc.of("metadata.consumer.Consumer"));
					assertEquals(6, reexported.exports().size());
					var freshReflection = new ReflectionEngine("metadata.thirdReflection", CONTEXT);
					var reexportedReflection = freshReflection.addHostModule((WasmModule)object);
					assertEquals(6, reexportedReflection.exports().size());
					// Imported tags retain the producer's class identity in emitted metadata.
					var tag = c.getAnnotationsByType(WasmTagExport.class)[0];
					assertSame(
						((ReflectionExport.TagExport)Objects.requireNonNull(loaded.exports().get("tag"))).tagClass(),
						tag.exception()
					);
				}
				try(var files = Files.walk(consumerOutput)) {
					assertFalse(
						files.anyMatch(
							p -> Objects.requireNonNull(p.getFileName()).toString().matches("(Struct|Array)[0-9].*")
						),
						"Loaded GC representations must be reused"
					);
				}
			}
		}
	}

	@Test
	public void resultCarriersMustImplementWasmResult() throws Exception {
		var wat = temporaryDirectory.resolve("unmarked-result.wat");
		Files.writeString(wat, """
			(module (func (export "pair") (result i32 i64) i32.const 1 i64.const 2))
			""");
		var output = temporaryDirectory.resolve("unmarked-result");
		Files.createDirectories(output);
		var producer = new AOTEngine("metadata.unmarked", classPath(output), output);
		var original = producer.emitModule(readWatFile(wat), "Producer", name -> {
			throw new AssertionError(name);
		});
		int changed = 0;
		try(var files = Files.walk(output)) {
			for(var file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
				var cf = ClassFile.of();
				var model = cf.parse(file);
				if(model.interfaces().stream()
					.noneMatch(i -> i.asSymbol().equals(ClassDesc.ofDescriptor(WasmResult.class.descriptorString()))))
					continue;
				Files.write(file, cf.transformClass(model, ClassTransform.dropping(e -> e instanceof Interfaces)));
				changed++;
			}
		}
		assertEquals(1, changed);
		var aot = new AOTEngine("metadata.rejectResult", classPath(output), temporaryDirectory.resolve("unused"));
		assertThrows(ModuleFormatException.class, () -> aot.addHostModule(original.classDesc()));
		try(var loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, getClass().getClassLoader())) {
			var moduleClass = loader.loadClass("metadata.unmarked.Producer");
			assertFalse(WasmResult.class.isAssignableFrom(moduleClass.getMethod("pair").getReturnType()));
			var instance = (WasmModule)moduleClass.getConstructor(RuntimeContext.class).newInstance(CONTEXT);
			var reflection = new ReflectionEngine("metadata.rejectResultReflection", CONTEXT);
			assertThrows(ModuleFormatException.class, () -> reflection.addHostModule(instance));
		}
	}

	@WasmSubtype
	@WasmStructType
	public static final class NamedStruct implements WasmStruct {
		private byte value;
		private NamedStruct(byte value) {
			this.value = value;
		}

		public static NamedStruct create(byte value) {
			return new NamedStruct(value);
		}

		@FieldAccessor(0)
		public byte readValue() {
			return value;
		}

		@FieldMutator(0)
		public void writeValue(byte value) {
			this.value = value;
		}
	}
	@WasmSubtype
	@WasmStructType
	public static class NamedBase implements WasmStruct {
		private final long value;
		private NamedBase(long value) {
			this.value = value;
		}

		public static NamedBase create(long value) {
			return new NamedBase(value);
		}

		@FieldAccessor(0)
		public long readValue() {
			return value;
		}
	}
	public static final class NamedHost extends WasmModule {
		@WasmExport(type = WasmExport.ExportType.FUNC)
		public ResultRef<NamedBase> base() {
			return ResultRef.of(NamedBase.create(1));
		}

		@WasmExport(type = WasmExport.ExportType.FUNC)
		public ResultRef<NamedStruct> make() {
			return ResultRef.of(NamedStruct.create((byte)7));
		}
	}
	@Test
	public void executesDiscoveredAccessorAndMutatorBindings() throws Exception {
		var consumer = readWatFile(Path.of("src/test/resources/compiler/metadata-named-bindings.wat"));
		var host = new NamedHost();
		var reflection = new ReflectionEngine("metadata.namedReflection", CONTEXT);
		var imported = reflection.addHostModule(host);
		var instance = reflection.instantiateModule(consumer, name -> imported);
		var run = ((ReflectionExport.FunctionExport)Objects.requireNonNull(instance.exports().get("run"))).method();
		assertEquals(42, ResultI32.get((ResultI32)Objects.requireNonNull(run.invoke(instance.module()))).item0);
		var testClasses = Path.of(
			Objects
				.requireNonNull(Objects.requireNonNull(getClass().getProtectionDomain().getCodeSource()).getLocation())
				.toURI()
		);
		var output = temporaryDirectory.resolve("named-bindings");
		var aot = new AOTEngine("metadata.namedAot", classPath(testClasses), output);
		var loaded = aot.addHostModule(ClassDesc.ofDescriptor(NamedHost.class.descriptorString()));
		aot.emitModule(consumer, "Consumer", name -> loaded);
		try(var loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, getClass().getClassLoader())) {
			var generated = loader.loadClass("metadata.namedAot.Consumer");
			var object = generated.getConstructor(RuntimeContext.class, NamedHost.class).newInstance(CONTEXT, host);
			assertEquals(
				42,
				ResultI32.get((ResultI32)Objects.requireNonNull(generated.getMethod("run").invoke(object))).item0
			);
		}
	}

	@Test
	public void representingClassesHaveMetadataAndCorrectFinality() throws Exception {
		var classes = compile("metadata-producer.wat");
		int structs = 0, arrays = 0, functions = 0;
		for(var model : classes) {
			if(AnnotatedTypeLoader.annotation(model, "WasmStructType").isPresent()) {
				structs++;
				boolean child = model.thisClass().asInternalName().endsWith("_child");
				assertEquals(child, model.flags().has(java.lang.reflect.AccessFlag.FINAL));
				assertTrue(
					model.methods().stream().filter(m -> m.methodName().equalsString("get0"))
						.allMatch(m -> m.findAttribute(Attributes.runtimeVisibleAnnotations()).isPresent())
				);
			}
			if(AnnotatedTypeLoader.annotation(model, "WasmArrayType").isPresent()) {
				arrays++;
				assertTrue(AnnotatedTypeLoader.annotation(model, "WasmArrayType").orElseThrow().elements().isEmpty());
				var getter = model.methods().stream()
					.filter(
						m -> m.methodName().equalsString("get") && !m.flags().has(java.lang.reflect.AccessFlag.BRIDGE)
					).findFirst().orElseThrow();
				assertTrue(
					getter.findAttribute(Attributes.runtimeVisibleAnnotations()).orElseThrow().annotations().stream()
						.anyMatch(
							a -> a.classSymbol().equals(ClassDesc.ofDescriptor(FieldAccessor.class.descriptorString()))
						)
				);
				assertEquals(
					!model.thisClass().asInternalName().endsWith("_a"),
					model.flags().has(java.lang.reflect.AccessFlag.FINAL)
				);
			}
			if(AnnotatedTypeLoader.annotation(model, "WasmFuncType").isPresent())
				functions++;
			if(model.thisClass().asInternalName().endsWith("$Impl"))
				assertTrue(AnnotatedTypeLoader.annotation(model, "WasmSubtype").isEmpty());
		}
		assertTrue(structs >= 2);
		assertTrue(arrays >= 1);
		assertTrue(functions >= 1);
	}
}
