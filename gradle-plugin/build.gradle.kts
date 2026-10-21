plugins {
    `java-gradle-plugin`
    `maven-publish`
}

group = "dev.argon.jawawasm"
version = "0.3.0-SNAPSHOT"

java.toolchain.languageVersion = JavaLanguageVersion.of(24)

dependencies {
    implementation(project(":engine"))
    compileOnly(libs.jspecify)
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("jawawasm") {
            id = "dev.argon.jawawasm"
            implementationClass = "dev.argon.jawawasm.gradle.JawaWasmPlugin"
            displayName = "JawaWasm AOT compiler"
            description = "Compile WebAssembly files to JVM classes using AOTEngine."
        }
    }
}
