import dev.argon.jawawasm.gradle.WatCompile

plugins {
    id("dev.argon.jawawasm") version "0.3.1"
}

repositories { mavenCentral() }
java.toolchain.languageVersion.set(JavaLanguageVersion.of(24))
dependencies {
    implementation("dev.argon.jawawasm:jawawasm-runtime:0.3.1")
}

val wasmBinary = layout.buildDirectory.file("wasm/greeting.wasm")
val compileWat = tasks.register<WatCompile>("compileWat") {
    source.set(layout.projectDirectory.file("src/main/wasm/greeting.wat"))
    outputFile.set(wasmBinary)
}

wasm {
    packageName.set("example.generated")
    val host = hostModules.create("Console") {
        className.set("example.ConsoleHost")
    }
    module("Greeting") {
        source.set(compileWat.map { wasmBinary.get() })
        imports.put("console", host)
    }
}
