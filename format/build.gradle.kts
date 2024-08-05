
plugins {
    id("jvmwasm.java-library-conventions")
    `maven-publish`
    signing
}


group = "dev.argon.jvmwasm"
version = "0.1.0"

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "wasm-format"
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}
