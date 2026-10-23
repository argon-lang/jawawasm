package dev.argon.jawawasm.engine.compiler;

import com.google.common.collect.ImmutableList;
import dev.argon.jawawasm.engine.internal.TypeUnroll;
import dev.argon.jawawasm.format.ModuleFormatException;
import dev.argon.jawawasm.format.types.*;
import java.lang.classfile.*;
import java.lang.constant.*;
import java.lang.reflect.AccessFlag;
import java.util.*;
import static java.lang.constant.ConstantDescs.*;

/** Shared decoder for concrete WebAssembly type declarations. */
public final class AnnotatedTypeLoader {
	/** Supplies dependency class models without loading JVM classes. */
	@FunctionalInterface
	public interface ClassModels {
		/**
		 * Reads a class model.
		 *
		 * @param type
		 *            the JVM class to read
		 * @return the class model
		 * @throws ModuleFormatException
		 *             if the class model cannot be read
		 */
		ClassModel load(ClassDesc type) throws ModuleFormatException;
	}
	/**
	 * Creates a decoder.
	 *
	 * @param compiler
	 *            the compiler receiving the decoded type bindings
	 * @param models
	 *            the source of dependency class models
	 */
	public AnnotatedTypeLoader(ModuleCompiler compiler, ClassModels models) {
		this.compiler = compiler;
		this.models = models;
	}
	private final ModuleCompiler compiler;
	private final ClassModels models;
	private final Map<ClassDesc, DefType> types = new HashMap<>();
	private final Set<ClassDesc> pending = new HashSet<>();

	/**
	 * Finds a runtime annotation by its runtime-package simple name.
	 *
	 * @param model
	 *            the class to inspect
	 * @param name
	 *            the annotation's simple name in the runtime package
	 * @return the annotation, or an empty optional if absent
	 */
	public static Optional<Annotation> annotation(ClassModel model, String name) {
		return model.findAttribute(Attributes.runtimeVisibleAnnotations()).stream()
			.flatMap(a -> a.annotations().stream())
			.filter(a -> a.classSymbol().equals(TypeAnnotations.annotationClass(name))).findFirst();
	}

	/**
	 * Reads an annotation element.
	 *
	 * @param annotation
	 *            the annotation to inspect
	 * @param name
	 *            the element name
	 * @return the explicitly stored value, or an empty optional if absent
	 */
	public static Optional<AnnotationValue> value(Annotation annotation, String name) {
		return annotation.elements().stream().filter(e -> e.name().stringValue().equals(name))
			.map(AnnotationElement::value).findFirst();
	}

	private static AnnotationValue required(Annotation a, String name) throws ModuleFormatException {
		return value(a, name)
			.orElseThrow(() -> new ModuleFormatException("Missing " + name + " in " + a.classSymbol()));
	}

	private static List<ClassDesc> classes(Annotation a, String name) {
		return value(a, name).map(
			v -> ((AnnotationValue.OfArray)v).values().stream().map(e -> ((AnnotationValue.OfClass)e).classSymbol())
				.toList()
		).orElse(List.of());
	}

	private static int index(Annotation a) {
		return value(a, "groupIndex").map(v -> ((AnnotationValue.OfInt)v).intValue()).orElse(0);
	}

	/**
	 * Reconstructs a complete recursive group and registers its JVM bindings.
	 *
	 * @param name
	 *            the JVM class representing the requested WebAssembly type
	 * @return the defined type represented by the class
	 * @throws ModuleFormatException
	 *             if the type declarations are invalid or a dependency class model
	 *             cannot be read
	 */
	public DefType load(ClassDesc name) throws ModuleFormatException {
		try {
			return loadImpl(name);
		}
		catch(IllegalArgumentException | ClassCastException | IndexOutOfBoundsException | NoSuchElementException e) {
			throw new ModuleFormatException("Malformed WebAssembly type annotations on " + name, e);
		}
	}

	private DefType loadImpl(ClassDesc name) throws ModuleFormatException {
		var cached = types.get(name);
		if(cached != null)
			return cached;
		if(!pending.add(name))
			throw new ModuleFormatException("Recursive references must share a recursive group: " + name);
		try {
			var root = models.load(name);
			var rootAnn = annotation(root, "WasmSubtype")
				.orElseThrow(() -> new ModuleFormatException("Missing WasmSubtype: " + name));
			var group = classes(rootAnn, "recursiveGroup");
			if(group.isEmpty())
				group = List.of(name);
			if(new HashSet<>(group).size() != group.size() || index(rootAnn) < 0 || index(rootAnn) >= group.size()
				|| !group.get(index(rootAnn)).equals(name))
				throw new ModuleFormatException("Invalid recursive group: " + name);
			var members = new ArrayList<ClassModel>();
			var subtypes = ImmutableList.<SubType>builder();
			for(int i = 0; i < group.size(); i++) {
				var model = models.load(group.get(i));
				members.add(model);
				var a = annotation(model, "WasmSubtype")
					.orElseThrow(() -> new ModuleFormatException("Missing recursive member subtype"));
				if(model.findAttribute(Attributes.signature())
					.map(x -> !x.asClassSignature().typeParameters().isEmpty()).orElse(false))
					throw new ModuleFormatException("Type parameters are forbidden on representing types");
				var declaredGroup = classes(a, "recursiveGroup");
				if(declaredGroup.isEmpty())
					declaredGroup = List.of(group.get(i));
				if(!declaredGroup.equals(group) || index(a) != i)
					throw new ModuleFormatException("Inconsistent recursive group metadata");
				var supers = ImmutableList.<HeapType>builder();
				for(var s : classes(a, "supertypes"))
					supers.add(heap(s, group));
				int count = 0;
				for(var kind : List.of("WasmStructType", "WasmArrayType", "WasmFuncType"))
					if(annotation(model, kind).isPresent())
						count++;
				if(count != 1)
					throw new ModuleFormatException(
						"Expected exactly one concrete type annotation: " + model.thisClass()
					);
				CompositeType composite;
				var st = annotation(model, "WasmStructType");
				var at = annotation(model, "WasmArrayType");
				if(st.isPresent()) {
					composite = new StructType(fields(model, group, false));
				}
				else if(at.isPresent())
					composite = new ArrayType(fields(model, group, true).getFirst());
				else {
					if(!model.flags().has(AccessFlag.INTERFACE))
						throw new ModuleFormatException("Function type must be an interface");
					var sam = sam(model);
					sams.put(model.thisClass().asSymbol(), sam);
					var args = ImmutableList.<ValType>builder();
					for(var arg : nodes(sam).arguments()) {
						var t = fromNode(arg, group);
						if(!(t instanceof ValType vt))
							throw new ModuleFormatException("Packed function parameter");
						args.add(vt);
					}
					composite = new FuncType(new ResultType(args.build()), result(nodes(sam).result(), group));
				}
				boolean isFinal = composite instanceof FuncType
					? ((AnnotationValue.OfBoolean)required(annotation(model, "WasmFuncType").orElseThrow(), "isFinal"))
						.booleanValue()
					: model.flags().has(AccessFlag.FINAL);
				subtypes.add(new SubType(isFinal, supers.build(), composite));
			}
			var rec = new RecursiveType(subtypes.build());
			try {
				for(int i = 0; i < group.size(); i++)
					types.put(group.get(i), new DefType(rec, i));
				// Stage every member before validating signatures containing group references.
				for(int i = 0; i < group.size(); i++)
					register(members.get(i), new DefType(rec, i));
				for(int i = 0; i < group.size(); i++)
					validate(members.get(i), new DefType(rec, i));
				members.forEach(compiler::registerLoadedHierarchy);
			}
			catch(ModuleFormatException | RuntimeException e) {
				for(int i = 0; i < group.size(); i++) {
					types.remove(group.get(i));
					compiler.unregisterLoadedType(new DefType(rec, i), group.get(i));
				}
				throw e;
			}
			return Objects.requireNonNull(types.get(name));
		}
		finally {
			pending.remove(name);
		}
	}

	private HeapType heap(ClassDesc name, List<ClassDesc> group) throws ModuleFormatException {
		int i = group.indexOf(name);
		return i >= 0 ? new RecTypeIdx(i) : load(name);
	}

	private void register(ClassModel model, DefType type) throws ModuleFormatException {
		var sub = TypeUnroll.unroll(type);
		var name = model.thisClass().asSymbol();
		DefTypeRealization realization = switch(sub.compositeType()) {
			case StructType st -> {
				var fields = new ArrayList<StructTypeRealization.Field>();
				for(int i = 0; i < st.fields().size(); i++) {
					var get = Objects.requireNonNull(Objects.requireNonNull(accessors.get(name)).get(i));
					var set = Objects.requireNonNull(mutators.get(name)).get(i);
					fields.add(
						new StructTypeRealization.Field(
							() -> get.methodTypeSymbol().returnType(),
							new StructTypeRealization.AccessMethod(
								get.methodName().stringValue(),
								get::methodTypeSymbol
							),
							set == null
								? null
								: new StructTypeRealization.AccessMethod(
									set.methodName().stringValue(),
									set::methodTypeSymbol
								)
						)
					);
				}
				yield new StructTypeRealization(
					name,
					model.flags().has(AccessFlag.INTERFACE),
					"create",
					() -> MethodTypeDesc.of(
						name,
						st.fields().stream().map(f -> compiler.getStorageType(f.storageType()).type()).toList()
					),
					fields,
					() -> sub.superTypes().isEmpty()
						? null
						: (StructTypeRealization)compiler.getDefType((DefType)sub.superTypes().getFirst())
				);
			}
			case ArrayType at -> new ArrayTypeRealization(
				name,
				() -> compiler.getStorageType(at.fieldType().storageType()).type(),
				Objects.requireNonNull(Objects.requireNonNull(accessors.get(name)).get(0)).methodName().stringValue(),
				Objects.requireNonNull(mutators.get(name)).containsKey(0)
					? Objects.requireNonNull(Objects.requireNonNull(mutators.get(name)).get(0)).methodName()
						.stringValue()
					: null,
				() -> sub.superTypes().isEmpty()
					? null
					: (ArrayTypeRealization)compiler.getDefType((DefType)sub.superTypes().getFirst())
			);
			case FuncType ft -> new FuncTypeRealization(
				name,
				Objects.requireNonNull(sams.get(name)).methodName().stringValue(),
				() -> compiler.getMethodType(ft),
				() -> sub.superTypes().isEmpty()
					? null
					: (FuncTypeRealization)compiler.getDefType((DefType)sub.superTypes().getFirst())
			);
		};
		compiler.registerLoadedType(type, realization);
	}

	private void method(ClassModel model, String name, MethodTypeDesc type, boolean isStatic)
		throws ModuleFormatException {
		if((name.equals("<init>") ? model.methods() : methods(model)).stream().noneMatch(
			m -> m.methodName().equalsString(name) && m.methodTypeSymbol().equals(type)
				&& m.flags().has(AccessFlag.PUBLIC) && m.flags().has(AccessFlag.STATIC) == isStatic
		))
			throw new ModuleFormatException("Missing JVM binding: " + model.thisClass() + "." + name + type);
	}

	private void validate(ClassModel model, DefType type) throws ModuleFormatException {
		var sub = TypeUnroll.unroll(type);
		var name = model.thisClass().asSymbol();
		if(sub.superTypes().size() > 1)
			throw new ModuleFormatException("Multiple Wasm supertypes are unsupported");
		var subtyping = new dev.argon.jawawasm.engine.internal.SubtypingBase() {
			@Override
			public HeapType resolveTypeIdx(dev.argon.jawawasm.format.modules.TypeIdx idx) {
				throw new IllegalArgumentException("Unresolved type index");
			}
		};
		for(var parent : sub.superTypes()) {
			var parentType = (DefType)parent;
			var parentSub = TypeUnroll.unroll(parentType);
			if(parentSub.isFinal() || !subtyping.isSubtypeComposite(sub.compositeType(), parentSub.compositeType()))
				throw new ModuleFormatException("Invalid Wasm supertype");
			if(!jvmSubtype(name, compiler.getDefType(parentType).classDesc(), new HashSet<>()))
				throw new ModuleFormatException("Wasm supertype does not match JVM hierarchy");
		}
		switch(sub.compositeType()) {
			case StructType st -> {
				if(!jvmSubtype(name, TypeAnnotations.annotationClass("WasmStruct"), new HashSet<>()))
					throw new ModuleFormatException("Struct must implement WasmStruct");
				method(
					model,
					"create",
					MethodTypeDesc.of(
						name,
						st.fields().stream().map(f -> compiler.getStorageType(f.storageType()).type()).toList()
					),
					true
				);

			}
			case ArrayType at -> {
				if(model.flags().has(AccessFlag.INTERFACE) || !jvmSubtype(
					name,
					TypeAnnotations
						.annotationClass(at.fieldType().mut() == Mut.Var ? "WasmArrayMutable" : "WasmArrayImmutable"),
					new HashSet<>()
				))
					throw new ModuleFormatException("Invalid array JVM hierarchy");
				var t = compiler.getStorageType(at.fieldType().storageType()).type();
				method(
					model,
					Objects.requireNonNull(Objects.requireNonNull(accessors.get(name)).get(0)).methodName()
						.stringValue(),
					MethodTypeDesc.of(t, CD_int),
					false
				);
				if(at.fieldType().mut() == Mut.Var) {
					method(
						model,
						Objects.requireNonNull(Objects.requireNonNull(mutators.get(name)).get(0)).methodName()
							.stringValue(),
						MethodTypeDesc.of(CD_void, CD_int, t),
						false
					);
					method(model, "fill", MethodTypeDesc.of(CD_void, CD_int, t, CD_int), false);
				}
				method(model, "nCopies", MethodTypeDesc.of(name, t, CD_int), true);
				method(model, "empty", MethodTypeDesc.of(name), true);
				method(model, "length", MethodTypeDesc.of(CD_int), false);
				method(model, "copyOfJavaArray", MethodTypeDesc.of(name, t.arrayType()), true);
				method(model, "copyOfJavaArray", MethodTypeDesc.of(name, CD_int, CD_int, t.arrayType()), true);
				boolean data = t.isPrimitive() || t.equals(TypeAnnotations.annotationClass("V128"));
				if(data) {
					method(model, "copyOfData", MethodTypeDesc.of(name, CD_int, CD_int, CD_byte.arrayType()), true);
					if(at.fieldType().mut() == Mut.Var)
						method(
							model,
							"copyFromData",
							MethodTypeDesc.of(CD_void, CD_int, CD_int, CD_int, CD_byte.arrayType()),
							false
						);
				}
				if(!(at.fieldType().storageType() instanceof RefType ref) || ref.isNullable())
					method(model, "ofDefault", MethodTypeDesc.of(name, CD_int), true);
			}
			case FuncType ft -> {
				if(!jvmSubtype(name, TypeAnnotations.annotationClass("WasmFunction"), new HashSet<>()))
					throw new ModuleFormatException("Function interface must extend WasmFunction");
				var invoke = Objects.requireNonNull(sams.get(name));
				method(model, invoke.methodName().stringValue(), compiler.getMethodType(ft).descriptor(), false);
			}
		}
	}

	private final Map<ClassDesc, Map<Integer, MethodModel>> accessors = new HashMap<>();
	private final Map<ClassDesc, Map<Integer, MethodModel>> mutators = new HashMap<>();
	private final Map<ClassDesc, MethodModel> sams = new HashMap<>();
	private List<MethodModel> methods(ClassModel model) throws ModuleFormatException {
		var all = new LinkedHashMap<String, MethodModel>();
		collectMethods(model, all, new HashSet<>());
		return List.copyOf(all.values());
	}

	private void collectMethods(ClassModel model, Map<String, MethodModel> methods, Set<ClassDesc> seen)
		throws ModuleFormatException {
		if(!seen.add(model.thisClass().asSymbol()))
			return;
		var parents = new ArrayList<ClassDesc>();
		model.superclass().ifPresent(s -> parents.add(s.asSymbol()));
		model.interfaces().forEach(s -> parents.add(s.asSymbol()));
		var overrides = new HashSet<String>();
		for(var m : model.methods())
			overrides.add(m.methodName().stringValue() + m.methodTypeSymbol().parameterList());
		for(var parent : parents) {
			if(parent.packageName().equals("java.lang"))
				continue;
			var inherited = new LinkedHashMap<String, MethodModel>();
			collectMethods(models.load(parent), inherited, new HashSet<>(seen));
			for(var entry : inherited.entrySet()) {
				var previous = methods.get(entry.getKey());
				var next = entry.getValue();
				if(previous != null) {
					var previousOwner = previous.parent().orElseThrow().thisClass().asSymbol();
					var nextOwner = next.parent().orElseThrow().thisClass().asSymbol();
					if(jvmSubtype(previousOwner, nextOwner, new HashSet<>()))
						continue;
					if(!jvmSubtype(nextOwner, previousOwner, new HashSet<>()) && !overrides.contains(entry.getKey())
						&& !sameBinding(previous, next))
						throw new ModuleFormatException("Conflicting inherited bindings: " + next.methodName());
				}
				methods.put(entry.getKey(), next);
			}
		}
		for(var m : model.methods().stream()
			.sorted(Comparator.comparingInt(m -> m.flags().has(AccessFlag.BRIDGE) ? 0 : 1)).toList()) {
			if(m.methodName().equalsString("<init>") || m.methodName().equalsString("<clinit>"))
				continue;
			String key = m.methodName().stringValue() + m.methodTypeSymbol().parameterList();
			methods.put(key, m);
		}
	}

	private boolean sameBinding(MethodModel a, MethodModel b) throws ModuleFormatException {
		if(!a.methodTypeSymbol().equals(b.methodTypeSymbol()))
			return false;
		for(var name : List.of("FieldAccessor", "FieldMutator")) {
			var aa = methodAnnotation(a, name);
			var ba = methodAnnotation(b, name);
			if(aa.isPresent() != ba.isPresent())
				return false;
			if(aa.isPresent() && ((AnnotationValue.OfInt)required(aa.get(), "value"))
				.intValue() != ((AnnotationValue.OfInt)required(ba.orElseThrow(), "value")).intValue())
				return false;
		}
		var an = nodes(a);
		var bn = nodes(b);
		var aNodes = new ArrayList<>(an.arguments());
		var bNodes = new ArrayList<>(bn.arguments());
		aNodes.add(an.result());
		bNodes.add(bn.result());
		for(int i = 0; i < aNodes.size(); i++) {
			if(!aNodes.get(i).signature().equals(bNodes.get(i).signature()))
				return false;
			for(var annotation : List.of("Nullable", "ExternRef", "NoneType"))
				if(aNodes.get(i).has(annotation) != bNodes.get(i).has(annotation))
					return false;
		}
		return true;
	}

	private Optional<Annotation> methodAnnotation(MethodModel m, String name) {
		return m.findAttribute(Attributes.runtimeVisibleAnnotations()).stream().flatMap(a -> a.annotations().stream())
			.filter(a -> a.classSymbol().equals(TypeAnnotations.annotationClass(name))).findFirst();
	}

	private void noTypeParameters(MethodModel m) throws ModuleFormatException {
		if(m.findAttribute(Attributes.signature()).map(a -> !a.asMethodSignature().typeParameters().isEmpty())
			.orElse(false))
			throw new ModuleFormatException("Type parameters are forbidden on binding methods: " + m.methodName());
	}

	private ImmutableList<FieldType> fields(ClassModel model, List<ClassDesc> group, boolean array)
		throws ModuleFormatException {
		var getters = new TreeMap<Integer, MethodModel>();
		var setters = new TreeMap<Integer, MethodModel>();
		for(var m : methods(model)) {
			var get = methodAnnotation(m, "FieldAccessor");
			var set = methodAnnotation(m, "FieldMutator");
			if(get.isEmpty() && set.isEmpty())
				continue;
			if(get.isPresent() && set.isPresent())
				throw new ModuleFormatException("Method cannot be both accessor and mutator");
			noTypeParameters(m);
			if(!m.flags().has(AccessFlag.PUBLIC) || m.flags().has(AccessFlag.STATIC))
				throw new ModuleFormatException("Field binding must be a public instance method");
			boolean accessor = get.isPresent();
			int i = ((AnnotationValue.OfInt)required(accessor ? get.orElseThrow() : set.orElseThrow(), "value"))
				.intValue();
			if(array && i != 0)
				throw new ModuleFormatException("Array element field index must be zero");
			if(i < 0)
				throw new ModuleFormatException("Negative field index");
			var desc = m.methodTypeSymbol();
			if(accessor
				? desc.parameterCount() != (array ? 1 : 0) || desc.returnType().equals(CD_void)
				: desc.parameterCount() != (array ? 2 : 1) || !desc.returnType().equals(CD_void))
				throw new ModuleFormatException("Invalid field binding signature: " + m.methodName());
			if(array && !desc.parameterType(0).equals(CD_int))
				throw new ModuleFormatException("Array element index parameter must be int");
			if((accessor ? getters : setters).putIfAbsent(i, m) != null)
				throw new ModuleFormatException("Duplicate field binding at " + i);
		}
		if(array && getters.size() != 1)
			throw new ModuleFormatException("Array must have exactly one element accessor");
		if(!getters.keySet().containsAll(setters.keySet()))
			throw new ModuleFormatException("Mutator has no accessor");
		var fields = ImmutableList.<FieldType>builder();
		for(int i = 0; i < getters.size(); i++) {
			var getter = getters.get(i);
			if(getter == null)
				throw new ModuleFormatException("Field indices must be contiguous");
			var node = nodes(getter).result();
			var storage = fromNode(node, group);
			var setter = setters.get(i);
			if(setter != null) {
				var argument = nodes(setter).arguments().get(array ? 1 : 0);
				if(!node.signature().equals(argument.signature()) || !storage.equals(fromNode(argument, group)))
					throw new ModuleFormatException("Accessor and mutator types differ at field " + i);
			}
			fields.add(new FieldType(storage, setter == null ? Mut.Const : Mut.Var));
		}
		accessors.put(model.thisClass().asSymbol(), getters);
		mutators.put(model.thisClass().asSymbol(), setters);
		return fields.build();
	}

	private MethodModel sam(ClassModel model) throws ModuleFormatException {
		var candidates = methods(model).stream()
			.filter(m -> m.flags().has(AccessFlag.ABSTRACT) && !m.flags().has(AccessFlag.STATIC) && !isObjectMethod(m))
			.toList();
		if(candidates.size() != 1)
			throw new ModuleFormatException("Function interface must have exactly one SAM: " + model.thisClass());
		var m = candidates.getFirst();
		noTypeParameters(m);
		if(!m.methodName().equalsString("invoke"))
			throw new ModuleFormatException("Function SAM must be named invoke");
		if(!m.flags().has(AccessFlag.PUBLIC))
			throw new ModuleFormatException("SAM must be public");
		return m;
	}

	private boolean isObjectMethod(MethodModel m) {
		return (m.methodName().equalsString("equals")
			&& m.methodTypeSymbol().equals(MethodTypeDesc.of(CD_boolean, CD_Object)))
			|| (m.methodName().equalsString("hashCode") && m.methodTypeSymbol().equals(MethodTypeDesc.of(CD_int)))
			|| (m.methodName().equalsString("toString") && m.methodTypeSymbol().equals(MethodTypeDesc.of(CD_String)));
	}
	private record Node(
		Signature signature,
		List<TypeAnnotation> annotations,
		List<TypeAnnotation.TypePathComponent> path
	) {
		boolean has(String name) {
			var cd = name.equals("Nullable")
				? ClassDesc.of("org.jspecify.annotations.Nullable")
				: TypeAnnotations.annotationClass(name);
			return annotations.stream()
				.anyMatch(a -> a.targetPath().equals(path) && a.annotation().classSymbol().equals(cd));
		}
	}
	private record MethodNodes(List<Node> arguments, Node result) {}
	private MethodNodes nodes(MethodModel method) {
		var anns = method.findAttribute(Attributes.runtimeVisibleTypeAnnotations()).map(a -> a.annotations())
			.orElse(List.of());
		var sig = Objects.requireNonNull(
			method.findAttribute(Attributes.signature()).map(a -> a.asMethodSignature())
				.orElseGet(() -> MethodSignature.of(method.methodTypeSymbol()))
		);
		var args = new ArrayList<Node>();
		for(int i = 0; i < sig.arguments().size(); i++) {
			int n = i;
			args.add(
				new Node(
					sig.arguments().get(i),
					anns.stream()
						.filter(
							a -> a.targetInfo() instanceof TypeAnnotation.FormalParameterTarget p
								&& p.formalParameterIndex() == n
						).toList(),
					List.of()
				)
			);
		}
		return new MethodNodes(
			args,
			new Node(
				sig.result(),
				anns.stream().filter(a -> a.targetInfo().targetType() == TypeAnnotation.TargetType.METHOD_RETURN)
					.toList(),
				List.of()
			)
		);
	}

	private Node argument(Node node, int i) throws ModuleFormatException {
		if(!(node.signature() instanceof Signature.ClassTypeSig cls) || i >= cls.typeArgs().size())
			throw new ModuleFormatException("Missing result type argument");
		var arg = cls.typeArgs().get(i);
		if(!(arg instanceof Signature.TypeArg.Bounded bound))
			throw new ModuleFormatException("Unbounded result type argument");
		if(bound.wildcardIndicator() == Signature.TypeArg.Bounded.WildcardIndicator.SUPER)
			throw new ModuleFormatException("Contravariant result type argument");
		var path = new ArrayList<>(node.path());
		path.add(TypeAnnotation.TypePathComponent.of(TypeAnnotation.TypePathComponent.Kind.TYPE_ARGUMENT, i));
		if(bound.wildcardIndicator() != Signature.TypeArg.Bounded.WildcardIndicator.NONE)
			path.add(TypeAnnotation.TypePathComponent.of(TypeAnnotation.TypePathComponent.Kind.WILDCARD, 0));
		return new Node(bound.boundType(), node.annotations(), path);
	}

	private StorageType fromNode(Node node, List<ClassDesc> group) throws ModuleFormatException {
		if(node.signature() instanceof Signature.BaseTypeSig b)
			return switch(b.baseType()) {
				case 'B' -> PackedType.I8;
				case 'S' -> PackedType.I16;
				case 'I' -> NumType.I32;
				case 'J' -> NumType.I64;
				case 'F' -> NumType.F32;
				case 'D' -> NumType.F64;
				default -> throw new ModuleFormatException("Unsupported value signature: " + b);
			};
		if(!(node.signature() instanceof Signature.ClassTypeSig c) || !c.typeArgs().isEmpty())
			throw new ModuleFormatException("Expected a concrete, nongeneric value type");
		var cd = c.classDesc();
		if(cd.equals(TypeAnnotations.annotationClass("V128")))
			return VecType.V128;
		HeapType h;
		String simple = cd.packageName().equals("dev.argon.jawawasm.runtime") ? cd.displayName() : "";
		h = switch(simple) {
			case "WasmFunction" ->
				node.has("NoneType") ? HeapType.AbstractHeapType.NOFUNC : HeapType.AbstractHeapType.FUNC;
			case "WebAssemblyException" ->
				node.has("NoneType") ? HeapType.AbstractHeapType.NOEXN : HeapType.AbstractHeapType.EXN;
			case "WasmEq" -> HeapType.AbstractHeapType.EQ;
			case "WasmStruct" -> HeapType.AbstractHeapType.STRUCT;
			case "WasmArray" -> HeapType.AbstractHeapType.ARRAY;
			case "I31" -> HeapType.AbstractHeapType.I31;
			default -> {
				if(cd.equals(CD_Object))
					yield node.has("ExternRef")
						? (node.has("NoneType") ? HeapType.AbstractHeapType.NOEXTERN : HeapType.AbstractHeapType.EXTERN)
						: (node.has("NoneType") ? HeapType.AbstractHeapType.NONE : HeapType.AbstractHeapType.ANY);
				yield heap(cd, group);
			}
		};
		return new RefType(node.has("Nullable"), h);
	}

	private ResultType result(Node node, List<ClassDesc> group) throws ModuleFormatException {
		if(!(node.signature() instanceof Signature.ClassTypeSig cls))
			throw new ModuleFormatException("SAM must return a WasmResult carrier");
		var model = models.load(cls.classDesc());
		if(!jvmSubtype(cls.classDesc(), TypeAnnotations.annotationClass("WasmResult"), new HashSet<>()))
			throw new ModuleFormatException("Result class must implement WasmResult");
		if(model.findAttribute(Attributes.innerClasses()).stream().flatMap(a -> a.classes().stream())
			.noneMatch(c -> c.innerClass().asSymbol().equals(cls.classDesc().nested("EndResult"))))
			throw new ModuleFormatException("Missing EndResult class");
		var of = model.methods().stream()
			.filter(
				m -> m.methodName().equalsString("of") && m.flags().has(AccessFlag.PUBLIC)
					&& m.flags().has(AccessFlag.STATIC)
			).toList();
		if(of.size() != 1 || !of.getFirst().methodTypeSymbol().returnType().equals(cls.classDesc()))
			throw new ModuleFormatException("Invalid result factory");

		var factorySignature = nodes(of.getFirst());
		var classParameters = model.findAttribute(Attributes.signature())
			.map(a -> a.asClassSignature().typeParameters()).orElse(List.of());
		int factoryReference = 0;
		for(var nodeParameter : factorySignature.arguments()) {
			if(nodeParameter.signature() instanceof Signature.TypeVarSig variable) {
				if(factoryReference >= classParameters.size()
					|| !variable.identifier().equals(classParameters.get(factoryReference++).identifier()))
					throw new ModuleFormatException("Result factory type parameters must match the class");
			}
			else if(!(nodeParameter.signature() instanceof Signature.BaseTypeSig))
				throw new ModuleFormatException("Result factory reference parameters must use type variables");
		}
		if(factoryReference != classParameters.size())
			throw new ModuleFormatException("Result factory type parameter count mismatch");
		var shape = ImmutableList.<TypeKind>builder();
		var values = ImmutableList.<ValType>builder();
		int reference = 0;
		for(var p : of.getFirst().methodTypeSymbol().parameterList()) {
			var kind = WasmClassGeneratorUtils.typeKind(p);
			shape.add(kind);
			StorageType storage = kind == TypeKind.REFERENCE
				? fromNode(argument(node, reference++), group)
				: fromNode(new Node(Signature.of(p), List.of(), List.of()), group);
			if(!(storage instanceof ValType v))
				throw new ModuleFormatException("Packed result value");
			values.add(v);
		}
		if(reference != cls.typeArgs().size())
			throw new ModuleFormatException("Result type argument count mismatch");
		var erased = new ErasedResultType(shape.build());
		validateResultClass(model, erased);
		compiler.registerResultType(erased, cls.classDesc());
		return new ResultType(values.build());
	}

	/**
	 * Validates the JVM operations used to consume a result carrier.
	 *
	 * @param model
	 *            the result carrier class model
	 * @param shape
	 *            the expected erased result types
	 * @throws ModuleFormatException
	 *             if the carrier does not support the expected operations or a
	 *             dependency class model cannot be read
	 */
	public void validateResultClass(ClassModel model, ErasedResultType shape) throws ModuleFormatException {
		var name = model.thisClass().asSymbol();
		if(!model.flags().has(AccessFlag.INTERFACE)
			|| !jvmSubtype(name, TypeAnnotations.annotationClass("WasmResult"), new HashSet<>()))
			throw new ModuleFormatException("Result carrier must be a WasmResult interface");
		var endName = name.nested("EndResult");
		method(model, "get", MethodTypeDesc.of(endName, name), true);
		var end = models.load(endName);
		if(!jvmSubtype(endName, name, new HashSet<>()))
			throw new ModuleFormatException("EndResult must implement its result carrier");
		for(int i = 0; i < shape.types().size(); i++) {
			int index = i;
			var expected = switch(shape.types().get(i)) {
				case INT -> CD_int;
				case LONG -> CD_long;
				case FLOAT -> CD_float;
				case DOUBLE -> CD_double;
				case REFERENCE -> CD_Object;
				default -> throw new ModuleFormatException("Invalid result shape");
			};
			if(end.fields().stream().noneMatch(
				f -> f.fieldName().equalsString("item" + index) && f.fieldTypeSymbol().equals(expected)
					&& f.flags().has(AccessFlag.PUBLIC) && !f.flags().has(AccessFlag.STATIC)
			))
				throw new ModuleFormatException("Invalid EndResult field at " + i);
		}
	}

	private boolean jvmSubtype(ClassDesc type, ClassDesc parent, Set<ClassDesc> seen) throws ModuleFormatException {
		if(type.equals(parent))
			return true;
		if(type.equals(CD_Object) || !seen.add(type))
			return false;
		var model = models.load(type);
		for(var iface : model.interfaces())
			if(jvmSubtype(iface.asSymbol(), parent, seen))
				return true;
		return model.superclass().isPresent() && jvmSubtype(model.superclass().orElseThrow().asSymbol(), parent, seen);
	}

	/**
	 * Checks an exported function against its explicitly declared function type.
	 *
	 * @param functionClass
	 *            the JVM interface declaring the function type
	 * @param method
	 *            the exported method to validate
	 * @return the declared WebAssembly function type
	 * @throws ModuleFormatException
	 *             if the function type cannot be loaded or the exported method does
	 *             not match it
	 */
	public DefType functionExport(ClassDesc functionClass, MethodModel method) throws ModuleFormatException {
		var type = load(functionClass);
		if(!(TypeUnroll.expand(type) instanceof FuncType expected))
			throw new ModuleFormatException("Function export does not reference a function type");
		if(!compiler.getMethodType(type).descriptor().equals(method.methodTypeSymbol()))
			throw new ModuleFormatException("Function export JVM signature does not match its defined type");
		var args = ImmutableList.<ValType>builder();
		for(var node : nodes(method).arguments()) {
			var storage = fromNode(node, List.of());
			if(!(storage instanceof ValType val))
				throw new ModuleFormatException("Packed function parameter");
			args.add(val);
		}
		var actual = new FuncType(new ResultType(args.build()), result(nodes(method).result(), List.of()));
		if(!actual.equals(expected))
			throw new ModuleFormatException("Function export metadata does not match its defined type");
		return type;
	}

	/**
	 * Loads explicit tag exports, preserving aliases and imported exception
	 * classes.
	 *
	 * @param module
	 *            the module class model to inspect
	 * @return the declared tag export realizations
	 * @throws ModuleFormatException
	 *             if a tag declaration is invalid or a dependency class model
	 *             cannot be read
	 */
	public List<WasmExportRealization> tagExports(ClassModel module) throws ModuleFormatException {
		var annotations = new ArrayList<Annotation>();
		annotation(module, "WasmTagExport").ifPresent(annotations::add);
		var container = annotation(module, "WasmTagExports");
		if(container.isPresent())
			for(var v : ((AnnotationValue.OfArray)required(container.get(), "value")).values())
				annotations.add(((AnnotationValue.OfAnnotation)v).annotation());
		var exports = new ArrayList<WasmExportRealization>();
		for(var a : annotations) {
			String name = ((AnnotationValue.OfString)required(a, "name")).stringValue();
			var exception = ((AnnotationValue.OfClass)required(a, "exception")).classSymbol();
			var function = load(((AnnotationValue.OfClass)required(a, "functionType")).classSymbol());
			if(!(TypeUnroll.expand(function) instanceof FuncType ft) || !ft.results().types().isEmpty())
				throw new ModuleFormatException("Tag must have a function type without results");
			var model = models.load(exception);
			if(!model.superclass()
				.map(c -> c.asSymbol().equals(TypeAnnotations.annotationClass("WebAssemblyException"))).orElse(false))
				throw new ModuleFormatException("Tag must extend WebAssemblyException");
			var params = ft.args().types().stream().map(t -> compiler.getValType(t).type()).toList();
			var ctor = MethodTypeDesc.of(CD_void, params);
			method(model, "<init>", ctor, false);
			var inner = model.findAttribute(Attributes.innerClasses()).stream().flatMap(x -> x.classes().stream())
				.filter(x -> x.innerClass().asSymbol().equals(exception)).findFirst()
				.orElseThrow(() -> new ModuleFormatException("Tag class must have inner-class metadata"));
			exports.add(new WasmExportRealization.OfInnerClass(name, exception, inner, ctor, ft, function));
		}
		return exports;
	}

}
