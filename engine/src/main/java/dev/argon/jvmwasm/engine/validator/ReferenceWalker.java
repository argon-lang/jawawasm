package dev.argon.jvmwasm.engine.validator;

import dev.argon.jvmwasm.format.instructions.ControlInstr;
import dev.argon.jvmwasm.format.instructions.Expr;
import dev.argon.jvmwasm.format.instructions.Instr;
import dev.argon.jvmwasm.format.instructions.ReferenceInstr;
import dev.argon.jvmwasm.format.modules.*;

class ReferenceWalker {

	public ReferenceWalker(Context context) {
		this.context = context;
	}

	private final Context context;

	public void walkGlobal(Global global) {
		walkExpr(global.init());
	}

	public void walkElem(Elem elem) {
		for(Expr expr : elem.init()) {
			walkExpr(expr);
		}

		switch(elem.mode()) {
			case ElemMode.Active active -> {
				walkExpr(active.offset());
			}
			case ElemMode.Declarative declarative -> {}
			case ElemMode.Passive passive -> {}
		}
	}

	public void walkData(Data data) {
		switch(data.mode()) {
			case DataMode.Passive() -> {}
			case DataMode.Active active -> walkExpr(active.offset());
		}
	}

	public void walkExport(Export export) {
		if(export.desc() instanceof ExportDesc.Func func) {
			context.addRef(func.func());
		}
	}

	public void walkExpr(Expr expr) {
		for(Instr instr : expr.body()) {
			walkInstr(instr);
		}
	}

	public void walkInstr(Instr instr) {
		switch(instr) {
			case ControlInstr.Block block -> walkExpr(new Expr(block.body()));
			case ControlInstr.Loop loop -> walkExpr(new Expr(loop.body()));
			case ControlInstr.If if_ -> {
				walkExpr(new Expr(if_.thenBody()));
				walkExpr(new Expr(if_.elseBody()));
			}
			case ControlInstr.Call(var funcIdx) -> context.addRef(funcIdx);
			case ControlInstr.Return_Call(var funcIdx) -> context.addRef(funcIdx);
			case ReferenceInstr.Ref_Func(var funcIdx) -> context.addRef(funcIdx);
			default -> {}
		}
	}
}
