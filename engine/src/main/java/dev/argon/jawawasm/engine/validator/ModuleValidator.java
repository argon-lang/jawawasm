package dev.argon.jawawasm.engine.validator;

import dev.argon.jawawasm.format.instructions.Expr;
import dev.argon.jawawasm.format.modules.*;
import dev.argon.jawawasm.format.modules.Module;
import dev.argon.jawawasm.format.types.FuncType;
import dev.argon.jawawasm.format.types.NumType;
import dev.argon.jawawasm.format.types.ResultType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates WebAssembly modules.
 */
public class ModuleValidator extends ValidatorBase {

	ModuleValidator(Context context) {
		super(context);
	}

	/**
	 * Validate a module.
	 * @param module The module to validate.
	 * @throws ValidationException if validation failed.
	 */
	public static void validateModule(Module module) throws ValidationException {
		Context c = new Context();

		var refWalker = new ReferenceWalker(c);

		int memCount = 0;

		for(FuncType t : module.types()) {
			c.addType(t);
		}

		for(Import import_ : module.imports()) {
			switch(import_.desc()) {
				case ImportDesc.Func func -> {
					c.requireType(func.type());
					var t = c.getType(func.type());
					c.addFunc(t);
				}
				case ImportDesc.Global global -> {
					c.addGlobal(global.type());
				}
				case ImportDesc.Table table -> {
					c.addTable(table.type());
				}
				case ImportDesc.Mem mem -> {
					c.addMem(mem.type());
					++memCount;
				}
			}
		}

		for(Func func : module.funcs()) {
			c.requireType(func.type());
			var t = c.getType(func.type());
			c.addFunc(t);
		}

		for(Global global : module.globals()) {
			refWalker.walkGlobal(global);
		}

		for(Table table : module.tables()) {
			c.addTable(table.type());
		}

		for(Mem mem : module.mems()) {
			++memCount;
			c.addMem(mem.type());
		}

		if(memCount > 1) {
			throw new ValidationException("multiple memories");
		}

		for(Elem elem : module.elems()) {
			c.addElem(elem.type());
			refWalker.walkElem(elem);
		}

		c.setDatas(module.datas().size());

		for(Data data : module.datas()) {
			refWalker.walkData(data);
		}

		for(Export export : module.exports()) {
			refWalker.walkExport(export);
		}

		var validator = new ModuleValidator(c);

		for(Table table : module.tables()) {
			validator.validateTable(table);
		}

		for(Mem mem : module.mems()) {
			validator.validateMem(mem);
		}

		for(Global global : module.globals()) {
			validator.validateGlobal(global);
		}

		for(Elem elem : module.elems()) {
			validator.validateElem(elem);
		}

		for(Data data : module.datas()) {
			validator.validateData(data);
		}

		for(Global global : module.globals()) {
			c.addGlobal(global.type());
		}

		for(Func func : module.funcs()) {
			validator.validateFunc(func);
		}

		if(module.start() != null) {
			validator.validateStart(module.start());
		}

		for(Import import_ : module.imports()) {
			validator.validateImport(import_);
		}

		Set<String> exportNames = new HashSet<>();
		for(Export export : module.exports()) {
			validator.validateExport(export);

			if(!exportNames.add(export.name())) {
				throw new ValidationException("duplicate export name");
			}
		}
	}

	private void validateFunc(Func func) throws ValidationException {
		var t = context.getType(func.type());
		var c = context.copy();
		c.addLocals(t.args().types());
		c.addLocals(func.locals());
		c.addLabel(t.results());
		c.setReturn(t.results());
		new InstrValidator(c).validateExpr(func.body(), t.results());
	}

	private void validateStart(Start start) throws ValidationException {
		context.requireFunc(start.func());

		var t = context.getFunc(start.func());
		require(t.args().types().size() == 0, "start function");
		require(t.results().types().size() == 0, "start function");
	}

	private void validateImport(Import import_) throws ValidationException {
		var tv = new TypeValidator(context);
		switch(import_.desc()) {
			case ImportDesc.Func(var t) -> context.requireType(t);
			case ImportDesc.Global(var t) -> {}
			case ImportDesc.Mem(var t) -> tv.validateMemoryType(t);
			case ImportDesc.Table(var t) -> tv.validateTableType(t);
		}
	}

	private void validateExport(Export export) throws ValidationException {
		switch(export.desc()) {
			case ExportDesc.Func(var f) -> context.requireFunc(f);
			case ExportDesc.Global(var g) -> context.requireGlobal(g);
			case ExportDesc.Mem(var m) -> context.requireMem(m);
			case ExportDesc.Table(var t) -> context.requireTable(t);
		}
	}

	private void validateTable(Table table) throws ValidationException {
		new TypeValidator(context).validateTableType(table.type());
	}

	private void validateMem(Mem mem) throws ValidationException {
		new TypeValidator(context).validateMemoryType(mem.type());
	}

	private void validateGlobal(Global global) throws ValidationException {
		var iv = new InstrValidator(context);
		iv.requireConstantExpr(global.init());
		iv.validateExpr(global.init(), new ResultType(List.of(global.type().type())));
	}

	private void validateElem(Elem elem) throws ValidationException {
		var iv = new InstrValidator(context);
		for(Expr expr : elem.init()) {
			iv.requireConstantExpr(expr);
			iv.validateExpr(expr, new ResultType(List.of(elem.type())));
		}

		switch(elem.mode()) {
			case ElemMode.Active active -> {
				context.requireTable(active.table());
				require(elem.type().equals(context.getTable(active.table()).elementType()), "type mismatch");
				iv.requireConstantExpr(active.offset());
				iv.validateExpr(active.offset(), new ResultType(List.of(NumType.I32)));
			}
			case ElemMode.Declarative declarative -> {}
			case ElemMode.Passive passive -> {}
		}
	}

	private void validateData(Data data) throws ValidationException {
		switch(data.mode()) {
			case DataMode.Active active -> {
				var iv = new InstrValidator(context);
				context.requireMem(active.memory());
				iv.requireConstantExpr(active.offset());
				iv.validateExpr(active.offset(), new ResultType(List.of(NumType.I32)));
			}
			case DataMode.Passive passive -> {}
		}
	}

}
