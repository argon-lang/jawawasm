package jvmwasm.engine;

public sealed interface FunctionResult {
	public static record Values(Object[] values) implements FunctionResult {}
	public static non-sealed interface Delay extends FunctionResult {
		FunctionResult step() throws Throwable;
	}


	public static Object[] resolve(FunctionResult result) throws Throwable {
		Object[] value = null;
		while(value == null) {
			switch(result) {
				case Values values -> value = values.values();
				case Delay delay -> result = delay.step();
			}
		}
		return value;
	}

}
