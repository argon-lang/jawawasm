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
            artifactId = "wasm-format-core"

            pom {
                name = "JawaWasm Format Core"
                description = "WebAssembly model and binary format reader in pure Java"
            }
        }
    }
}
