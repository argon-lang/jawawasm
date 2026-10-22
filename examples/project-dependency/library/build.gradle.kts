plugins {
    `java-library`
    id("dev.argon.jawawasm") version "0.3.0"
}

repositories { mavenCentral() }
java.toolchain.languageVersion.set(JavaLanguageVersion.of(24))
dependencies {
    api("dev.argon.jawawasm:jawawasm-runtime:0.3.0")
}

val libraryWasm = layout.buildDirectory.file("wasm/library.wasm")
val compileWat = tasks.register<Exec>("compileWat") {
    val wat = layout.projectDirectory.file("src/main/wasm/library.wat")
    val output = libraryWasm.get().asFile
    inputs.file(wat)
    outputs.file(libraryWasm)
    executable(providers.gradleProperty("wasmTools").getOrElse("wasm-tools"))
    args("parse", wat.asFile.absolutePath, "-o", output.absolutePath)
    doFirst { output.parentFile.mkdirs() }
}

wasm {
    packageName.set("example.library")
    module("Library") {
        source.set(compileWat.map { libraryWasm.get() })
    }
}
