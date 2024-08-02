package jvmwasm.engine.validator;

import jvmwasm.format.modules.*;
import jvmwasm.format.types.*;
import org.jspecify.annotations.Nullable;

import java.util.*;

public class Context {

	private final List<FuncType> types = new ArrayList<>();
	private final List<FuncType> funcs = new ArrayList<>();
	private final List<TableType> tables = new ArrayList<>();
	private final List<MemType> mems = new ArrayList<>();
	private final List<GlobalType> globals = new ArrayList<>();
	private final List<RefType> elems = new ArrayList<>();
	private int datas = 0;
	private final List<ValType> locals = new ArrayList<>();
	private final List<ResultType> labels = new ArrayList<>();
	private @Nullable ResultType return_ = null;
	private Set<FuncIdx> refs = new HashSet<>();

	public Context copy() {
		var other = new Context();
		other.types.addAll(types);
		other.funcs.addAll(funcs);
		other.tables.addAll(tables);
		other.mems.addAll(mems);
		other.globals.addAll(globals);
		other.elems.addAll(elems);
		other.datas = datas;
		other.locals.addAll(locals);
		other.labels.addAll(labels);
		other.return_ = return_;
		other.refs.addAll(refs);
		return other;
	}


	public void requireType(TypeIdx idx) throws ValidationException {
		if(!(idx.index() >= 0 && idx.index() < types.size())) {
			throw new ValidationException("unknown type " + idx.index());
		}
	}
	public FuncType getType(TypeIdx idx) {
		return types.get(idx.index());
	}

	public void addType(FuncType t) {
		types.add(t);
	}


	public void requireFunc(FuncIdx idx) throws ValidationException {
		if(!(idx.index() >= 0 && idx.index() < funcs.size())) {
			throw new ValidationException("unknown function " + idx.index());
		}
	}
	public FuncType getFunc(FuncIdx idx) {
		return funcs.get(idx.index());
	}

	public void addFunc(FuncType t) {
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

	public void requireLabel(LabelIdx labelIdx) throws ValidationException {
		if(!(labelIdx.index() >= 0 && labelIdx.index() < labels.size())) {
			throw new ValidationException("unknown label " + labelIdx.index());
		}
	}
	public ResultType getLabel(LabelIdx labelIdx) {
		return labels.get(labelIdx.index());
	}
	public void addLabel(ResultType t) {
		labels.add(0, t);
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
}
