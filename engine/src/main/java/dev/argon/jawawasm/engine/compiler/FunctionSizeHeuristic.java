package dev.argon.jawawasm.engine.compiler;

import dev.argon.jawawasm.format.instructions.ControlInstr;
import dev.argon.jawawasm.format.instructions.Instr;
import dev.argon.jawawasm.format.instructions.ReferenceInstr;
import dev.argon.jawawasm.format.modules.Func;

import java.util.ArrayDeque;
import java.util.Iterator;

/** Decides whether direct emission needs an isolated method-size check. */
final class FunctionSizeHeuristic {
	private FunctionSizeHeuristic() {}

	// This is a staging threshold, not a prediction of the encoded method size.
	static final long LARGE_FUNCTION_THRESHOLD = 32 * 1024;

	static boolean isLarge(Func function, int parameterCount) {
		long estimate = 128L + 8L * (parameterCount + (long)function.locals().size());
		var bodies = new ArrayDeque<Iterator<Instr>>();
		bodies.push(function.body().body().iterator());
		while (!bodies.isEmpty()) {
			if(estimate >= LARGE_FUNCTION_THRESHOLD) {
				return true;
			}
			var body = bodies.getFirst();
			if(!body.hasNext()) {
				bodies.pop();
				continue;
			}
			var instruction = body.next();
			estimate += 32;
			switch(instruction) {
				case ControlInstr.Block(var _, var nested) -> bodies.push(nested.iterator());
				case ControlInstr.Loop(var _, var nested) -> bodies.push(nested.iterator());
				case ControlInstr.If(var _, var thenBody, var elseBody) -> {
					bodies.push(elseBody.iterator());
					bodies.push(thenBody.iterator());
				}
				case ControlInstr.Try_Table(var _, var catches, var nested) -> {
					estimate += 32L * catches.size();
					bodies.push(nested.iterator());
				}
				case ControlInstr.Br_Table(var targets, var _) -> estimate += 4L * targets.size();
				case ReferenceInstr.Array_New_Fixed(var _, var count) -> estimate += 32L * count;
				default -> {
				}
			}
		}
		return estimate >= LARGE_FUNCTION_THRESHOLD;
	}
}
