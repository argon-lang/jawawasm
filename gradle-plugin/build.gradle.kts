import org.gradle.plugin.compatibility.compatibility

plugins {
    alias(libs.plugins.plugin.publish)
}

java.toolchain.languageVersion = JavaLanguageVersion.of(24)

dependencies {
    implementation(project(":engine"))
    implementation(project(":format"))
    compileOnly(libs.jspecify)
    testImplementation(gradleTestKit())
}

gradlePlugin {
    website.set("https://github.com/argon-lang/jawawasm")
    vcsUrl.set("https://github.com/argon-lang/jawawasm.git")

    plugins {
        create("jawawasm") {
            id = "dev.argon.jawawasm"
            implementationClass = "dev.argon.jawawasm.gradle.JawaWasmPlugin"
            displayName = "JawaWasm AOT compiler"
            description = "Compile WebAssembly files to JVM classes using AOTEngine."
            tags.set(listOf("webassembly", "wasm", "jvm", "aot"))
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
    }
}
