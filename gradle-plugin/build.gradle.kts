import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-gradle-plugin`
    id("net.ltgt.errorprone") version "4.1.0"
}


group = "dev.argon.jawawasm"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":engine"))
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

//    withSourcesJar()
//    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableWarningsInGeneratedCode.set(true)

        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
        error("NullAway")
    }

    options.compilerArgs.add("-Xlint:unchecked,deprecation,fallthrough,path,rawtypes")
}
