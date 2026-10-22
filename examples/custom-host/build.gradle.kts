plugins {
    id("dev.argon.jawawasm") version "0.3.0"
}

repositories { mavenCentral() }
java.toolchain.languageVersion.set(JavaLanguageVersion.of(24))
dependencies {
    implementation("dev.argon.jawawasm:jawawasm-runtime:0.3.0")
}

val wasmBinary = layout.buildDirectory.file("wasm/greeting.wasm")
val compileWat = tasks.register<Exec>("compileWat") {
    val wat = layout.projectDirectory.file("src/main/wasm/greeting.wat")
    val output = wasmBinary.get().asFile
    inputs.file(wat)
    outputs.file(wasmBinary)
    executable(providers.gradleProperty("wasmTools").getOrElse("wasm-tools"))
    args("parse", wat.asFile.absolutePath, "-o", output.absolutePath)
    doFirst { output.parentFile.mkdirs() }
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
