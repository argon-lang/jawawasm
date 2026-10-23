import dev.argon.jawawasm.gradle.WatCompile

plugins {
    `java-library`
    id("dev.argon.jawawasm") version "0.3.1"
}

repositories { mavenCentral() }
java.toolchain.languageVersion.set(JavaLanguageVersion.of(24))
dependencies {
    api("dev.argon.jawawasm:jawawasm-runtime:0.3.1")
}

val libraryWasm = layout.buildDirectory.file("wasm/library.wasm")
val compileWat = tasks.register<WatCompile>("compileWat") {
    source.set(layout.projectDirectory.file("src/main/wasm/library.wat"))
    outputFile.set(libraryWasm)
}

wasm {
    packageName.set("example.library")
    module("Library") {
        source.set(compileWat.map { libraryWasm.get() })
    }
}
