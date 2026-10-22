package example;

import example.generated.Answer;

/** Generated Wasm classes are available to main Java sources. */
public final class Modules {
    private Modules() {}

    public static Class<Answer> moduleType() {
        return Answer.class;
    }
}
