plugins {
    `java-library`
}

dependencies {
    api(project(":runtime"))
    api(libs.guava)
    api(libs.protobuf)
    compileOnly(libs.jspecify)
}

publishing {
    publications {
        named<MavenPublication>("mavenJava") {
            artifactId = "wasm-format"

            pom {
                name = "JawaWasm Format"
                description = "WebAssembly format reader in pure Java"
            }
        }
    }
}
