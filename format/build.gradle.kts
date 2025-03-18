import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    `maven-publish`
    signing
    id("net.ltgt.errorprone") version "4.1.0"
}


group = "dev.argon.jawawasm"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":runtime"))
    compileOnly(libs.jspecify)
    testImplementation(libs.junit.jupiter)

    errorprone(libs.nullaway)
    errorprone(libs.errorprone)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(22)
    }

    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableWarningsInGeneratedCode.set(true)

        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
    }
}

tasks.compileJava {
    options.errorprone.error("NullAway")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "wasm-format"
            from(components["java"])

            pom {
                name = "JawaWasm Format"
                description = "WebAssembly format reader in pure Java"
                url = "https://github.com/argon-lang/jawawasm"
                licenses {
                    license {
                        name = "GNU Lesser General Public License, Version 3"
                        url = "https://www.gnu.org/licenses/lgpl-3.0.en.html"
                    }
                }
                developers {
                    developer {
                        name = "argon-dev"
                        email = "argon@argon.dev"
                        organization = "argon-lang"
                        organizationUrl = "https://argon.dev"
                    }
                }
                scm {
                    connection = "scm:git:git@github.com:argon-lang/jawawasm.git"
                    developerConnection = "scm:git:git@github.com:argon-lang/jawawasm.git"
                    url = "https://github.com/argon-lang/jawawasm/tree/master"
                }
            }
        }
    }

    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}
