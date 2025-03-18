package dev.argon.jawawasm.engine.validator;

import dev.argon.jawawasm.engine.internal.TypeRoll;
import dev.argon.jawawasm.format.instructions.Expr;
import dev.argon.jawawasm.format.modules.*;
import dev.argon.jawawasm.format.modules.Module;
import dev.argon.jawawasm.format.types.*;

import java.util.ArrayList;
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

		int typeIndex = 0;
		for(RecursiveType recType : module.types()) {
			var rolledRecType = TypeRoll.roll(recType, typeIndex);
			for(int i = 0; i < rolledRecType.subtypes().size(); ++i) {
				c.addType(new DefType(rolledRecType, i));
			}

			new TypeValidator(c).validateRecursiveType(recType, typeIndex);
			typeIndex += recType.subtypes().size();
		}

		for(Import import_ : module.imports()) {
			switch(import_.desc()) {
				case ImportDesc.Func func -> {
					c.requireType(func.type());
					c.addFunc(func.type());
				}
				case ImportDesc.Global global -> {
					c.addGlobal(global.type());
				}
				case ImportDesc.Table table -> {
					c.addTable(table.type());
				}
				case ImportDesc.Mem mem -> {
					c.addMem(mem.type());
				}
				case ImportDesc.Tag tag -> {
					c.addTag(tag.type());
				}
			}
		}

		for(Func func : module.funcs()) {
			c.requireType(func.type());
			c.addFunc(func.type());
		}

		for(Global global : module.globals()) {
			refWalker.walkGlobal(global);
		}

		for(Table table : module.tables()) {
			c.addTable(table.type());
			refWalker.walkTable(table);
		}

		for(Mem mem : module.mems()) {
			c.addMem(mem.type());
		}

		for(Tag tag : module.tags()) {
			c.addTag(tag.type());
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

		for(Tag tag : module.tags()) {
			validator.validateTag(tag);
		}

		for(Global global : module.globals()) {
			validator.validateGlobal(global);
			c.addGlobal(global.type());
		}

		for(Elem elem : module.elems()) {
			validator.validateElem(elem);
		}

		for(Data data : module.datas()) {
			validator.validateData(data);
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
		context.requireFuncType(func.type());
		var t = (FuncType)context.getCompositeType(func.type());
		var c = context.copy();
		c.addLocalsInit(t.args().types());

		var tv = new TypeValidator(context);
		for(var local : func.locals()) {
			tv.validateValType(local);
		}

		c.addLocals(func.locals());
		c.addLabel(t.results());
		c.setReturn(t.results());
		new InstrValidator(c).validateExpr(func.body(), t.results());
	}

	private void validateStart(Start start) throws ValidationException {
		context.requireFunc(start.func());

		var t = context.getFuncType(start.func());
		require(t.args().types().isEmpty(), "start function");
		require(t.results().types().isEmpty(), "start function");
	}

	private void validateImport(Import import_) throws ValidationException {
		var tv = new TypeValidator(context);
		switch(import_.desc()) {
			case ImportDesc.Func(var t) -> context.requireType(t);
			case ImportDesc.Global(var t) -> {}
			case ImportDesc.Mem(var t) -> tv.validateMemoryType(t);
			case ImportDesc.Table(var t) -> tv.validateTableType(t);
			case ImportDesc.Tag(var t) -> tv.validateTagType(t);
		}
	}

	private void validateExport(Export export) throws ValidationException {
		switch(export.desc()) {
			case ExportDesc.Func(var f) -> context.requireFunc(f);
			case ExportDesc.Global(var g) -> context.requireGlobal(g);
			case ExportDesc.Mem(var m) -> context.requireMem(m);
			case ExportDesc.Table(var t) -> context.requireTable(t);
			case ExportDesc.Tag(var t) -> context.requireTag(t);
		}
	}

	private void validateTable(Table table) throws ValidationException {
		new TypeValidator(context).validateTableType(table.type());
		new InstrValidator(context).validateExpr(table.init(), new ResultType(List.of(table.type().elementType())));
	}

	private void validateMem(Mem mem) throws ValidationException {
		new TypeValidator(context).validateMemoryType(mem.type());
	}

	private void validateTag(Tag tag) throws ValidationException {
		new TypeValidator(context).validateTagType(tag.type());
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

		new TypeValidator(context).validateReferenceType(elem.type());

		switch(elem.mode()) {
			case ElemMode.Active active -> {
				context.requireTable(active.table());
				var table = context.getTable(active.table());
				var tableElementType = table.elementType();
				new TypeValidator(context).validateReferenceType(tableElementType);

				require(new Subtyping(context).isSubtypeRef(elem.type(), tableElementType), "type mismatch " + elem.type() + ", " + tableElementType);
				iv.requireConstantExpr(active.offset());
				iv.validateExpr(active.offset(), new ResultType(List.of(AddressTypeUtils.asNumType(table.addrType()))));
			}
			case ElemMode.Declarative(), ElemMode.Passive() -> {}
		}
	}

	private void validateData(Data data) throws ValidationException {
		switch(data.mode()) {
			case DataMode.Active active -> {
				var iv = new InstrValidator(context);
				context.requireMem(active.memory());
				var memory = context.getMem(active.memory());
				iv.requireConstantExpr(active.offset());
				iv.validateExpr(active.offset(), new ResultType(List.of(AddressTypeUtils.asNumType(memory.addrType()))));
			}
			case DataMode.Passive() -> {}
		}
	}

}
