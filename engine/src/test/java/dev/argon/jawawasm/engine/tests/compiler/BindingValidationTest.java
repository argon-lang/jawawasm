package dev.argon.jawawasm.engine.tests.compiler;

import com.google.common.collect.ImmutableList;
import dev.argon.jawawasm.engine.aot.AOTEngine;
import dev.argon.jawawasm.engine.reflection.ReflectionEngine;
import dev.argon.jawawasm.format.ModuleFormatException;
import dev.argon.jawawasm.runtime.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.constant.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import static java.lang.constant.ConstantDescs.*;
import static org.junit.jupiter.api.Assertions.*;

public class BindingValidationTest {
	@TempDir
	public Path temporaryDirectory;
	private static ClassDesc runtime(String name) {
		return ClassDesc.of("dev.argon.jawawasm.runtime." + name);
	}

	private static Annotation annotation(String name) {
		return Annotation.of(runtime(name));
	}

	private static Annotation index(String name, int index) {
		return Annotation.of(runtime(name), List.of(AnnotationElement.of("value", AnnotationValue.ofInt(index))));
	}

	private static void binding(
		ClassBuilder clb,
		String name,
		ClassDesc returns,
		Annotation annotation,
		ClassDesc... args
	) {
		clb.withMethod(
			name,
			MethodTypeDesc.of(returns, args),
			ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT,
			mb -> mb.with(RuntimeVisibleAnnotationsAttribute.of(annotation))
		);
	}

	private byte[] type(String example, ClassDesc type) {
		if(example.startsWith("array-"))
			return arrayType(example, type);
		return ClassFile.of().build(type, clb -> {
			clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT);
			clb.withInterfaceSymbols(
				example.equals("inherited") || example.equals("inherited-conflict")
					? type.nested("Parent")
					: runtime(example.startsWith("sam") ? "WasmFunction" : "WasmStruct")
			);
			if(example.equals("inherited-conflict"))
				clb.withInterfaceSymbols(type.nested("Parent"), type.nested("Other"));
			clb.with(
				RuntimeVisibleAnnotationsAttribute.of(
					example.equals("group-invalid")
						? Annotation.of(
							runtime("WasmSubtype"),
							List.of(AnnotationElement.of("groupIndex", AnnotationValue.ofInt(1)))
						)
						: annotation("WasmSubtype"),
					example.startsWith("sam")
						? Annotation.of(
							runtime("WasmFuncType"),
							List.of(AnnotationElement.of("isFinal", AnnotationValue.ofBoolean(true)))
						)
						: annotation("WasmStructType")
				)
			);
			if(example.startsWith("sam")) {
				clb.withMethod(
					example.equals("sam-name") ? "apply" : "invoke",
					MethodTypeDesc.of(runtime("ResultVoid")),
					ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT,
					mb -> {}
				);
				if(example.equals("sam-helper"))
					clb.withMethodBody(
						"helper",
						MethodTypeDesc.of(runtime("ResultVoid")),
						ClassFile.ACC_PUBLIC,
						cb -> cb.aconst_null().areturn()
					);
				if(example.equals("sam-multiple"))
					clb.withMethod(
						"other",
						MethodTypeDesc.of(runtime("ResultVoid")),
						ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT,
						mb -> {}
					);
				if(example.equals("sam-generic"))
					clb.with(
						SignatureAttribute.of(
							ClassSignature.parseFrom(
								"<T:Ljava/lang/Object;>Ljava/lang/Object;Ldev/argon/jawawasm/runtime/WasmFunction;"
							)
						)
					);
				return;
			}
			if(!example.equals("missing-accessor") && !example.equals("inherited"))
				binding(
					clb,
					"read",
					CD_byte,
					index("FieldAccessor", example.equals("gap") ? 1 : 0),
					example.equals("getter-argument") ? new ClassDesc[]{CD_int} : new ClassDesc[0]
				);
			if(example.equals("duplicate"))
				binding(clb, "alsoRead", CD_byte, index("FieldAccessor", 0));
			if(example.equals("void-getter"))
				binding(clb, "empty", CD_void, index("FieldAccessor", 1));
			if(example.equals("negative"))
				binding(clb, "negative", CD_int, index("FieldAccessor", -1));
			if(example.equals("generic-method"))
				clb.withMethod(
					"generic",
					MethodTypeDesc.of(CD_Object),
					ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT,
					mb -> {
						mb.with(RuntimeVisibleAnnotationsAttribute.of(index("FieldAccessor", 1)));
						mb.with(SignatureAttribute.of(MethodSignature.parseFrom("<T:Ljava/lang/Object;>()TT;")));
					}
				);
			if(example.equals("class-generic"))
				clb.with(
					SignatureAttribute.of(
						ClassSignature.parseFrom(
							"<T:Ljava/lang/Object;>Ljava/lang/Object;Ldev/argon/jawawasm/runtime/WasmStruct;"
						)
					)
				);
			if(example.equals("reference-metadata")) {
				clb.withMethod(
					"reference",
					MethodTypeDesc.of(CD_Object),
					ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT,
					mb -> {
						mb.with(RuntimeVisibleAnnotationsAttribute.of(index("FieldAccessor", 1)));
						mb.with(
							RuntimeVisibleTypeAnnotationsAttribute.of(
								TypeAnnotation.of(
									TypeAnnotation.TargetInfo.ofMethodReturn(),
									List.of(),
									Annotation.of(ClassDesc.of("org.jspecify.annotations.Nullable"))
								)
							)
						);
					}
				);
				binding(clb, "reference", CD_void, index("FieldMutator", 1), CD_Object);
			}
			binding(
				clb,
				"write",
				example.equals("mutator-return") ? CD_int : CD_void,
				index("FieldMutator", 0),
				example.equals("mismatch")
					? new ClassDesc[]{CD_short}
					: example.equals("mutator-arguments") ? new ClassDesc[0] : new ClassDesc[]{CD_byte}
			);
			clb.withMethodBody(
				"create",
				MethodTypeDesc.of(
					type,
					example.equals("inherited") ? new ClassDesc[]{CD_byte, CD_Object} : new ClassDesc[]{CD_byte}
				),
				ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
				cb -> cb.aconst_null().areturn()
			);
		});
	}

	private byte[] arrayType(String example, ClassDesc type) {
		return ClassFile.of().build(type, clb -> {
			clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT);
			boolean immutable = example.equals("array-immutable");
			clb.withSuperclass(runtime(immutable ? "WasmArrayImmutable" : "WasmArrayMutable"));
			clb.with(RuntimeVisibleAnnotationsAttribute.of(annotation("WasmSubtype"), annotation("WasmArrayType")));
			if(!example.equals("array-no-accessor"))
				binding(
					clb,
					"read",
					example.equals("array-void-getter") ? CD_void : CD_byte,
					index("FieldAccessor", example.equals("array-field-index") ? 1 : 0),
					example.equals("array-getter-arguments")
						? new ClassDesc[]{}
						: new ClassDesc[]{example.equals("array-getter-index") ? CD_long : CD_int}
				);
			if(example.equals("array-duplicate"))
				binding(clb, "anotherRead", CD_byte, index("FieldAccessor", 0), CD_int);
			if(!immutable)
				binding(
					clb,
					"write",
					example.equals("array-mutator-return") ? CD_int : CD_void,
					index("FieldMutator", 0),
					example.equals("array-mutator-arguments")
						? new ClassDesc[]{CD_byte}
						: new ClassDesc[]{example.equals("array-mutator-index") ? CD_long : CD_int,
								example.equals("array-mismatch") ? CD_short : CD_byte}
				);
			if(!immutable) {
				clb.withMethod(
					"fill",
					MethodTypeDesc.of(CD_void, CD_int, CD_byte, CD_int),
					ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT,
					mb -> {}
				);
				clb.withMethod(
					"copyFromData",
					MethodTypeDesc.of(CD_void, CD_int, CD_int, CD_int, CD_byte.arrayType()),
					ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT,
					mb -> {}
				);
			}
			for(var factory : List.of(
				java.util.Map.entry("nCopies", MethodTypeDesc.of(type, CD_byte, CD_int)),
				java.util.Map.entry("empty", MethodTypeDesc.of(type)),
				java.util.Map.entry("copyOfJavaArray", MethodTypeDesc.of(type, CD_byte.arrayType())),
				java.util.Map.entry("copyOfJavaArray", MethodTypeDesc.of(type, CD_int, CD_int, CD_byte.arrayType())),
				java.util.Map.entry("copyOfData", MethodTypeDesc.of(type, CD_int, CD_int, CD_byte.arrayType())),
				java.util.Map.entry("ofDefault", MethodTypeDesc.of(type, CD_int))
			)) {
				clb.withMethodBody(
					factory.getKey(),
					factory.getValue(),
					ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
					cb -> cb.aconst_null().areturn()
				);
			}
		});
	}

	@Test
	public void rejectsMalformedBindingsThroughBothLoaders() throws Exception {
		for(var example : List.of(
			"array-no-accessor",
			"array-void-getter",
			"array-field-index",
			"array-getter-arguments",
			"array-getter-index",
			"array-duplicate",
			"array-mutator-return",
			"array-mutator-arguments",
			"array-mutator-index",
			"array-mismatch",
			"super-wildcard",
			"sam-name",
			"sam-multiple",
			"sam-generic",
			"group-invalid",
			"inherited-conflict",
			"reference-metadata",
			"class-generic",
			"missing-accessor",
			"gap",
			"getter-argument",
			"duplicate",
			"void-getter",
			"negative",
			"generic-method",
			"mismatch",
			"mutator-return",
			"mutator-arguments"
		)) {
			check(example, false);
		}
	}

	@Test
	public void acceptsArbitraryIndexedMethodNames() throws Exception {
		check("array-valid", true);
		check("array-immutable", true);
		check("valid", true);
		check("inherited", true);
		check("sam-valid", true);
		check("sam-helper", true);
	}

	private void check(String example, boolean valid) throws Exception {
		var output = temporaryDirectory.resolve(example);
		var type = ClassDesc.of("binding." + example.replace('-', '_') + ".Value");
		var host = ClassDesc.of(type.packageName(), "Host");
		var cf = ClassFile.of();
		if(example.startsWith("inherited")) {
			for(var parent : List.of("Parent", "Other"))
				write(output, type.nested(parent), cf.build(type.nested(parent), clb -> {
					clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT);
					clb.withInterfaceSymbols(runtime("WasmStruct"));
					binding(clb, "read", CD_byte, index("FieldAccessor", 0));
					clb.withMethod(
						"reference",
						MethodTypeDesc.of(CD_Object),
						ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT,
						mb -> {
							mb.with(RuntimeVisibleAnnotationsAttribute.of(index("FieldAccessor", 1)));
							if(parent.equals("Parent"))
								mb.with(
									RuntimeVisibleTypeAnnotationsAttribute.of(
										TypeAnnotation.of(
											TypeAnnotation.TargetInfo.ofMethodReturn(),
											List.of(),
											Annotation.of(ClassDesc.of("org.jspecify.annotations.Nullable"))
										)
									)
								);
						}
					);
				}));
		}
		write(output, type, type(example, type));
		write(output, host, cf.build(host, clb -> {
			clb.withFlags(ClassFile.ACC_PUBLIC);
			clb.withSuperclass(runtime("WasmModule"));
			clb.withMethodBody(
				"<init>",
				MethodTypeDesc.of(CD_void),
				ClassFile.ACC_PUBLIC,
				cb -> cb.aload(0).invokespecial(runtime("WasmModule"), "<init>", MethodTypeDesc.of(CD_void)).return_()
			);

			if(example.equals("super-wildcard")) {
				clb.withMethod("use", MethodTypeDesc.of(runtime("ResultRef")), ClassFile.ACC_PUBLIC, mb -> {
					mb.with(
						SignatureAttribute.of(
							MethodSignature
								.parseFrom("()Ldev/argon/jawawasm/runtime/ResultRef<-" + type.descriptorString() + ">;")
						)
					);
					mb.with(
						RuntimeVisibleAnnotationsAttribute.of(
							Annotation.of(
								runtime("WasmExport"),
								List.of(
									AnnotationElement
										.of("type", AnnotationValue.ofEnum(runtime("WasmExport$ExportType"), "FUNC"))
								)
							)
						)
					);
					mb.withCode(cb -> cb.aconst_null().areturn());
				});
				return;
			}
			clb.withMethod("use", MethodTypeDesc.of(runtime("ResultVoid"), type), ClassFile.ACC_PUBLIC, mb -> {
				mb.with(
					RuntimeVisibleAnnotationsAttribute.of(
						Annotation.of(
							runtime("WasmExport"),
							List.of(
								AnnotationElement
									.of("type", AnnotationValue.ofEnum(runtime("WasmExport$ExportType"), "FUNC"))
							)
						)
					)
				);
				mb.withCode(cb -> cb.aconst_null().areturn());
			});
		}));
		var runtimePath = Path.of(
			Objects.requireNonNull(
				Objects.requireNonNull(ResultVoid.class.getProtectionDomain().getCodeSource()).getLocation()
			).toURI()
		);
		var aot = new AOTEngine("binding.consumer", ImmutableList.of(output, runtimePath), output.resolve("unused"));
		try(var loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, getClass().getClassLoader())) {
			var module = (WasmModule)loader.loadClass(host.packageName() + ".Host").getConstructor().newInstance();
			var reflection = new ReflectionEngine("binding.reflection", RuntimeOptions::new);
			if(valid) {
				assertDoesNotThrow(() -> aot.addHostModule(host));
				assertDoesNotThrow(() -> reflection.addHostModule(module));
			}
			else {
				assertThrows(ModuleFormatException.class, () -> aot.addHostModule(host), example + " (AOT)");
				assertThrows(
					ModuleFormatException.class,
					() -> reflection.addHostModule(module),
					example + " (reflection)"
				);
			}
		}
	}

	private static void write(Path output, ClassDesc type, byte[] bytes) throws Exception {
		var path = output
			.resolve(type.descriptorString().substring(1, type.descriptorString().length() - 1) + ".class");
		Files.createDirectories(Objects.requireNonNull(path.getParent()));
		Files.write(path, bytes);
	}
}
