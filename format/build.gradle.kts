
plugins {
    id("jawawasm.java-library-conventions")
    `maven-publish`
    signing
}


group = "dev.argon.jawawasm"
version = "0.1.0"

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "wasm-format"
            from(components["java"])
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}
