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
    api(project(":format"))
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

tasks.javadoc {
    val sourceSetDirectories = sourceSets
        .main
        .get()
        .java
        .sourceDirectories
        .joinToString(":")

    val coreOptions = options as CoreJavadocOptions
    coreOptions.addStringOption("-source-path", sourceSetDirectories)

    exclude("dev/argon/jawawasm/engine/internal/**")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "wasm-engine"
            from(components["java"])

            pom {
                name = "JawaWasm"
                description = "WebAssembly interpreter written in pure Java"
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
