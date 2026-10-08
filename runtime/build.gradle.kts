import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    `maven-publish`
    signing
    alias(libs.plugins.errorprone)
}


group = "dev.argon.jawawasm"
version = "0.2.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.jspecify)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

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

tasks.named<Test>("test") {
    useJUnitPlatform()
}

val testGenerator = sourceSets.create("testGenerator")
val generatedV128Tests = layout.buildDirectory.dir("generated/sources/v128Test/java")
val wasmTestSuite = rootProject.layout.projectDirectory.dir("webassembly-testsuite")

val generateV128Tests = tasks.register<JavaExec>("generateV128Tests") {
    group = "verification"
    description = "Generate direct V128 tests from the WebAssembly SIMD suite."
    classpath = testGenerator.runtimeClasspath
    mainClass.set("dev.argon.jawawasm.runtime.generator.GenerateV128Tests")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    args(wasmTestSuite.asFile.absolutePath, generatedV128Tests.get().asFile.absolutePath)
    inputs.files(fileTree(wasmTestSuite) { include("simd_*.wast") })
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(generatedV128Tests)
}

sourceSets.test {
    java.srcDir(generateV128Tests)
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableWarningsInGeneratedCode.set(true)

        disable("RefactorSwitch")
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
        error("NullAway")
    }

    options.compilerArgs.add("-Xlint:unchecked,deprecation,fallthrough,path,rawtypes")
    options.compilerArgs.add("-parameters")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "jawawasm-runtime"
            from(components["java"])

            pom {
                name = "JawaWasm Runtime"
                description = "Types used at runtime for WebAssembly by JawaWasm"
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
