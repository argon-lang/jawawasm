/**
 * Driver loaded in isolation by the reflection tests.
 */
module dev.argon.jawawasm.reflection.driver {
    requires static org.jspecify;
    requires dev.argon.jawawasm.engine;

    exports dev.argon.jawawasm.reflection.driver;
}
