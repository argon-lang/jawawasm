package dev.argon.jawawasm.engine.validator;

import com.google.common.collect.ImmutableList;
import dev.argon.jawawasm.format.instructions.Instr;
import dev.argon.jawawasm.format.types.ResultType;

/** A body and its scoped context and signature, owned by one queued job. */
public class CodeValidateJob {
	public CodeValidateJob(Context context, ResultType argTypes, ResultType resultType, ImmutableList<Instr> body, boolean requireConstant) {
		this.context = context;
		this.argTypes = argTypes;
		this.resultType = resultType;
		this.body = body;
		this.requireConstant = requireConstant;
	}

	private final Context context;
	private final ResultType argTypes;
	private final ResultType resultType;
	private final ImmutableList<Instr> body;
	private final boolean requireConstant;

	public boolean requireConstant() {
		return requireConstant;
	}

	public Context context() {
		return context;
	}

	public ResultType argTypes() {
		return argTypes;
	}

	public ResultType resultType() {
		return resultType;
	}

	public ImmutableList<Instr> body() {
		return body;
	}

}
