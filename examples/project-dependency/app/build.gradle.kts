import dev.argon.jawawasm.gradle.WatCompile

plugins {
    application
    id("dev.argon.jawawasm") version "0.3.1"
}

application { mainClass.set("example.Modules") }

repositories { mavenCentral() }
java.toolchain.languageVersion.set(JavaLanguageVersion.of(24))
dependencies {
    implementation(project(":library"))
}

val wasmBinary = layout.buildDirectory.file("wasm/application.wasm")
val compileWat = tasks.register<WatCompile>("compileWat") {
    source.set(layout.projectDirectory.file("src/main/wasm/application.wat"))
    outputFile.set(wasmBinary)
}

wasm {
    packageName.set("example.generated")
    // The dependency already contains JVM classes compiled by :library:compileWasm.
    val library = hostModules.create("Library") {
        className.set("example.library.LibraryHost")
    }
    module("Application") {
        source.set(compileWat.map { wasmBinary.get() })
        imports.put("library", library)
    }
}
