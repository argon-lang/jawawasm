
plugins {
    `java-library`
    `maven-publish`
    signing
}


group = "dev.argon.jawawasm"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.jspecify:jspecify:0.3.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.1")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(22)
    }

    withSourcesJar()
    withJavadocJar()
}

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
