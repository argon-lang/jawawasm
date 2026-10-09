import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
}

dependencies {
    api(project(":format-core"))
    api(libs.jspecify) // Not compile only because we may use reflection
    implementation(libs.commons.compress)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disable("InvalidBlockTag")
        disable("MissingSummary")
    }
}

val testCompilerExport = "dev.argon.jawawasm.engine/dev.argon.jawawasm.engine.compiler=dev.argon.jawawasm.engine.tests"

val testInternalExport = "dev.argon.jawawasm.engine/dev.argon.jawawasm.engine.internal=dev.argon.jawawasm.engine.tests"

tasks.named<JavaCompile>("compileTestJava") {
    options.compilerArgs.addAll(listOf("--add-exports", testInternalExport, "--add-exports", testCompilerExport))
}

tasks.named<Test>("test") {
    jvmArgs("--add-exports", testInternalExport, "--add-exports", testCompilerExport)
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}

tasks.javadoc {
    val sourceSetDirectories = sourceSets
        .main
        .get()
        .java
        .sourceDirectories
        .joinToString(":")

    val coreOptions = options as CoreJavadocOptions
    coreOptions.addStringOption("-source-path", sourceSetDirectories)

    exclude("dev/argon/jawawasm/engine/internal")
}

publishing {
    publications {
        named<MavenPublication>("mavenJava") {
            artifactId = "wasm-engine"

            pom {
                name = "JawaWasm"
                description = "WebAssembly interpreter written in pure Java"
            }
        }
    }
}
