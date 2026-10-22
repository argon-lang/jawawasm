package example;

import example.generated.Greeting;

/** Both the host and generated module are available to main Java sources. */
public final class Modules {
    private Modules() {}

    public static Class<Greeting> moduleType() {
        return Greeting.class;
    }

    public static ConsoleHost newHost() {
        return new ConsoleHost();
    }
}
