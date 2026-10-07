import net.ltgt.gradle.errorprone.errorprone

plugins {
    application
    java
    alias(libs.plugins.errorprone)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":format"))
    implementation(project(":engine"))
    implementation(project(":runtime"))

    implementation(libs.jackson.core)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.guava)
    implementation(libs.commons.io)
    compileOnly(libs.jspecify)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    errorprone(libs.nullaway)
    errorprone(libs.errorprone)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableWarningsInGeneratedCode.set(true)

        disable("RefactorSwitch")
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
        error("NullAway")
        disable("DefaultCharset")
    }

    options.compilerArgs.add("-Xlint:unchecked,deprecation,fallthrough,path,rawtypes")
    options.compilerArgs.add("-parameters")
}

application {
    mainClass.set("dev.argon.jawawasm.app.App")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}
