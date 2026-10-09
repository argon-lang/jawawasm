plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

// Compile the callback contract independently of the generated parser.
val parserHost = sourceSets.create("parserHost")
val parserGenerator = sourceSets.create("parserGenerator")

dependencies {
    api(project(":format-core"))
    api(libs.jackson.annotations)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.guava)
    compileOnly(libs.jspecify)
    add(parserHost.implementationConfigurationName, project(":runtime"))
    add(parserHost.compileOnlyConfigurationName, libs.jspecify)
    add(parserGenerator.implementationConfigurationName, project(":engine"))
    add(parserGenerator.compileOnlyConfigurationName, libs.jspecify)
}

val parserCrate = rootProject.layout.projectDirectory.dir("jawawasm-format-text")
val cargoTarget = layout.buildDirectory.dir("cargo")
val parserWasm = cargoTarget.map {
    it.file("wasm32-unknown-unknown/release/jawawasm_format_text.wasm")
}
val generatedParser = layout.buildDirectory.dir("generated/text-parser")

val buildTextParserWasm = tasks.register<Exec>("buildTextParserWasm") {
    group = "build"
    description = "Compile the Rust text parser to WebAssembly."
    inputs.files(parserCrate.file("Cargo.toml"), parserCrate.file("Cargo.lock"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(fileTree(parserCrate.dir("src")) { include("**/*.rs") })
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(parserWasm)
    commandLine(
        "cargo", "build", "--locked", "--release", "--target", "wasm32-unknown-unknown",
        "--manifest-path", parserCrate.file("Cargo.toml").asFile.absolutePath,
        "--target-dir", cargoTarget.get().asFile.absolutePath,
    )
}

val generateTextParser = tasks.register<JavaExec>("generateTextParser") {
    group = "build"
    description = "Compile the WebAssembly text parser to JVM classes using AOTEngine."
    dependsOn(buildTextParserWasm)
    classpath = parserGenerator.runtimeClasspath + parserHost.output
    mainClass.set("dev.argon.jawawasm.format.generator.GenerateTextParser")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    inputs.file(parserWasm).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(generatedParser)
    args(parserWasm.get().asFile.absolutePath, generatedParser.get().asFile.absolutePath)
}

sourceSets.main {
    java.srcDir(parserHost.java)
    output.dir(mapOf("builtBy" to generateTextParser), generatedParser)
}

val parserPatch = generatedParser.map {
    "dev.argon.jawawasm.format=${it.asFile.absolutePath}"
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(generateTextParser)
    options.compilerArgs.addAll(listOf("--patch-module", parserPatch.get()))
}

tasks.named<JavaCompile>("compileTestJava") {
    options.compilerArgs.addAll(listOf("--patch-module", parserPatch.get()))
}

tasks.named<Test>("test") {
    jvmArgs("--patch-module", parserPatch.get())
}

tasks.javadoc {
    dependsOn(generateTextParser)
    exclude("**/internal/**")
    val coreOptions = options as CoreJavadocOptions
    coreOptions.addStringOption("-source-path", sourceSets.main.get().java.sourceDirectories.asPath)
    coreOptions.addStringOption("-patch-module", parserPatch.get())
}

publishing {
    publications {
        named<MavenPublication>("mavenJava") {
            artifactId = "wasm-format"

            pom {
                name = "JawaWasm Format"
                description = "WebAssembly format facade with binary and text readers"
            }
        }
    }
}
