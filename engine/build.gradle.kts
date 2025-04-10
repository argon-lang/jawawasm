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
    api(libs.jspecify) // Not compile only because we may use reflection
    implementation(libs.commons.compress)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    errorprone(libs.nullaway)
    errorprone(libs.errorprone)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }

    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableWarningsInGeneratedCode.set(true)

        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
        error("NullAway")
        disable("InvalidBlockTag")
        disable("MissingSummary")
    }

    options.compilerArgs.add("-Xlint:unchecked,deprecation,fallthrough,path,rawtypes")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
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

    exclude("dev/argon/jawawasm/engine/internal")
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
