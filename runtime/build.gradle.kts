plugins {
    `java-library`
}

dependencies {
    compileOnly(libs.jspecify)
}

val testGenerator = sourceSets.create("testGenerator")

dependencies {
    add(testGenerator.compileOnlyConfigurationName, libs.jspecify)
}
val generatedV128Tests = layout.buildDirectory.dir("generated/sources/v128Test/java")
val wasmTestSuite = rootProject.layout.projectDirectory.dir("webassembly-testsuite")

val generateV128Tests = tasks.register<JavaExec>("generateV128Tests") {
    group = "verification"
    description = "Generate direct V128 tests from the WebAssembly SIMD suite."
    classpath = testGenerator.runtimeClasspath
    mainClass.set("dev.argon.jawawasm.runtime.generator.GenerateV128Tests")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    args(wasmTestSuite.asFile.absolutePath, generatedV128Tests.get().asFile.absolutePath)
    inputs.files(fileTree(wasmTestSuite) { include("simd_*.wast") })
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(generatedV128Tests)
}

sourceSets.test {
    java.srcDir(generateV128Tests)
}

publishing {
    publications {
        named<MavenPublication>("mavenJava") {
            artifactId = "jawawasm-runtime"

            pom {
                name = "JawaWasm Runtime"
                description = "Types used at runtime for WebAssembly by JawaWasm"
            }
        }
    }
}
