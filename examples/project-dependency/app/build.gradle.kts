plugins {
    application
    id("dev.argon.jawawasm") version "0.3.0"
}

application { mainClass.set("example.Modules") }

repositories { mavenCentral() }
java.toolchain.languageVersion.set(JavaLanguageVersion.of(24))
dependencies {
    implementation(project(":library"))
}

val wasmBinary = layout.buildDirectory.file("wasm/application.wasm")
val compileWat = tasks.register<Exec>("compileWat") {
    val wat = layout.projectDirectory.file("src/main/wasm/application.wat")
    val output = wasmBinary.get().asFile
    inputs.file(wat)
    outputs.file(wasmBinary)
    executable(providers.gradleProperty("wasmTools").getOrElse("wasm-tools"))
    args("parse", wat.asFile.absolutePath, "-o", output.absolutePath)
    doFirst { output.parentFile.mkdirs() }
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
