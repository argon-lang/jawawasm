package dev.argon.jawawasm.engine.validator;

import dev.argon.jawawasm.engine.internal.TypeUnroll;
import dev.argon.jawawasm.engine.internal.AppendOnlyList;
import dev.argon.jawawasm.engine.internal.CopyOnWriteSet;
import dev.argon.jawawasm.format.modules.*;
import dev.argon.jawawasm.format.types.*;
import org.jspecify.annotations.Nullable;

import java.util.*;

class Context {

	// Each immutable definition owns a lazy expansion shared by context clones.
	private final AppendOnlyList<CachedType> types;

	private static final class CachedType {
		CachedType(DefType definition) {
			this.definition = definition;
		}

		private final DefType definition;
		private volatile @Nullable CompositeType expanded;

		CompositeType expand() {
			var result = expanded;
			if(result == null) {
				synchronized (this) {
					result = expanded;
					if(result == null) {
						result = TypeUnroll.expand(definition);
						expanded = result;
					}
				}
			}
			return result;
		}
	}
	private final AppendOnlyList<TypeIdx> funcs;
	private final AppendOnlyList<TableType> tables;
	private final AppendOnlyList<MemType> mems;
	private final AppendOnlyList<TagType> tags;
	private final AppendOnlyList<GlobalType> globals;
	private final AppendOnlyList<RefType> elems;
	private int datas = 0;
	private final AppendOnlyList<ValType> locals;
	private int initializedLocalPrefix;
	private @Nullable BitSet initLocals;
	private boolean initLocalsShared;
	private final AppendOnlyList<ResultType> labels;
	private @Nullable ResultType return_ = null;
	private final CopyOnWriteSet<FuncIdx> refs;

	Context() {
		types = new AppendOnlyList<>();
		funcs = new AppendOnlyList<>();
		tables = new AppendOnlyList<>();
		mems = new AppendOnlyList<>();
		tags = new AppendOnlyList<>();
		globals = new AppendOnlyList<>();
		elems = new AppendOnlyList<>();
		locals = new AppendOnlyList<>();
		labels = new AppendOnlyList<>();
		refs = new CopyOnWriteSet<>();
	}

	private Context(Context source) {
		types = source.types.clone();
		funcs = source.funcs.clone();
		tables = source.tables.clone();
		mems = source.mems.clone();
		tags = source.tags.clone();
		globals = source.globals.clone();
		elems = source.elems.clone();
		datas = source.datas;
		locals = source.locals.clone();
		initializedLocalPrefix = source.initializedLocalPrefix;
		initLocals = source.initLocals;
		if(initLocals != null) {
			source.initLocalsShared = true;
			initLocalsShared = true;
		}
		labels = source.labels.clone();
		return_ = source.return_;
		refs = source.refs.clone();
	}

	public Context copy() {
		return new Context(this);
	}

	public void requireType(TypeIdx idx) throws ValidationException {
		if(!(idx.index() >= 0 && idx.index() < types.size())) {
			throw new ValidationException("unknown type " + idx.index());
		}
	}

	public void requireFuncType(TypeIdx idx) throws ValidationException {
		requireType(idx);
		if(!(getCompositeType(idx) instanceof FuncType)) {
			throw new ValidationException("Function type expected");
		}
	}

	public void requireStructType(TypeIdx idx) throws ValidationException {
		requireType(idx);
		if(!(getCompositeType(idx) instanceof StructType)) {
			throw new ValidationException("Function type expected");
		}
	}

	public void requireArrayType(TypeIdx idx) throws ValidationException {
		requireType(idx);
		if(!(getCompositeType(idx) instanceof ArrayType)) {
			throw new ValidationException("Function type expected");
		}
	}

	public DefType getType(TypeIdx idx) {
		return types.get(idx.index()).definition;
	}

	public CompositeType getCompositeType(TypeIdx idx) {
		return types.get(idx.index()).expand();
	}

	public void addType(DefType t) {
		types.add(new CachedType(t));
	}

	public void requireFunc(FuncIdx idx) throws ValidationException {
		if(!(idx.index() >= 0 && idx.index() < funcs.size())) {
			throw new ValidationException("unknown function " + idx.index());
		}
	}

	public TypeIdx getFunc(FuncIdx idx) {
		return funcs.get(idx.index());
	}

	public FuncType getFuncType(FuncIdx idx) {
		return (FuncType)getCompositeType(getFunc(idx));
	}

	public void addFunc(TypeIdx t) {
		funcs.add(t);
	}

	public void requireTable(TableIdx idx) throws ValidationException {
		if(!(idx.index() >= 0 && idx.index() < tables.size())) {
			throw new ValidationException("unknown table " + idx.index());
		}
	}

	public TableType getTable(TableIdx idx) {
		return tables.get(idx.index());
	}

	public void addTable(TableType t) {
		tables.add(t);
	}

	public void requireMem(MemIdx idx) throws ValidationException {
		if(!(idx.index() >= 0 && idx.index() < mems.size())) {
			throw new ValidationException("unknown memory " + idx.index());
		}
	}

	MemType getMem(MemIdx idx) {
		return mems.get(idx.index());
	}

	void addMem(MemType t) {
		mems.add(t);
	}

	public void requireTag(TagIdx idx) throws ValidationException {
		if(!(idx.index() >= 0 && idx.index() < tags.size())) {
			throw new ValidationException("unknown tag " + idx.index());
		}
	}

	public TagType getTag(TagIdx idx) {
		return tags.get(idx.index());
	}

	public void addTag(TagType t) {
		tags.add(t);
	}

	public void requireGlobal(GlobalIdx idx) throws ValidationException {
		if(!(idx.index() >= 0 && idx.index() < globals.size())) {
			throw new ValidationException("unknown global " + idx.index());
		}
	}

	public GlobalType getGlobal(GlobalIdx idx) {
		return globals.get(idx.index());
	}

	public void addGlobal(GlobalType t) {
		globals.add(t);
	}

	public void requireElem(ElemIdx idx) throws ValidationException {
		if(!(idx.index() >= 0 && idx.index() < elems.size())) {
			throw new ValidationException("unknown elem segment " + idx.index());
		}
	}

	public RefType getElem(ElemIdx idx) {
		return elems.get(idx.index());
	}

	public void addElem(RefType t) {
		elems.add(t);
	}

	public void requireData(DataIdx idx) throws ValidationException {
		if(!(idx.index() >= 0 && idx.index() < datas)) {
			throw new ValidationException("unknown data segment " + idx.index());
		}
	}

	public void setDatas(int datas) {
		this.datas = datas;
	}

	public void requireRef(FuncIdx idx) throws ValidationException {
		if(!refs.contains(idx)) {
			throw new ValidationException("undeclared function reference " + idx.index());
		}
	}

	void addRef(FuncIdx idx) {
		refs.add(idx);
	}

	public void requireLocal(LocalIdx local) throws ValidationException {
		if(!(local.index() >= 0 && local.index() < locals.size())) {
			throw new ValidationException("unknown local " + local.index());
		}
	}

	public ValType getLocal(LocalIdx local) {
		return locals.get(local.index());
	}

	public void addLocals(Collection<? extends ValType> types) {
		locals.addAll(types);
	}

	public void addLocalsInit(Collection<? extends ValType> types) {
		// Function parameters are the initial prefix of the local type table.
		locals.addAll(types);
		initializedLocalPrefix = locals.size();
	}

	public void requireInitLocal(LocalIdx local) throws ValidationException {
		int index = local.index();
		if(index < initializedLocalPrefix || isDefaultable(locals.get(index))) {
			return;
		}
		var initialized = initLocals;
		if(initialized == null || !initialized.get(index)) {
			throw new ValidationException("uninitialized local " + index);
		}
	}

	public void initializeLocal(LocalIdx local) {
		int index = local.index();
		if(index < initializedLocalPrefix || isDefaultable(locals.get(index))) {
			return;
		}
		var initialized = initLocals;
		if(initialized == null) {
			initialized = new BitSet();
			initLocals = initialized;
			initLocalsShared = false;
		}
		else {
			if(initialized.get(index))
				return;
			if(initLocalsShared) {
				initialized = (BitSet)initialized.clone();
				initLocals = initialized;
				initLocalsShared = false;
			}
		}
		initialized.set(index);
	}

	public void requireLabel(LabelIdx labelIdx) throws ValidationException {
		if(!(labelIdx.index() >= 0 && labelIdx.index() < labels.size())) {
			throw new ValidationException("unknown label " + labelIdx.index());
		}
	}

	public ResultType getLabel(LabelIdx labelIdx) {
		return labels.get(labels.size() - 1 - labelIdx.index());
	}

	public void addLabel(ResultType t) {
		labels.add(t);
	}

	public void requireReturn() throws ValidationException {
		if(return_ == null) {
			throw new ValidationException("missing return type in context");
		}
	}

	public ResultType getReturn() {
		if(return_ == null) {
			throw new IllegalStateException();
		}
		return return_;
	}

	public void setReturn(ResultType t) {
		return_ = t;
	}

	public boolean isDefaultable(StorageType t) {
		return !(t instanceof RefType rt) || rt.isNullable();
	}

}
