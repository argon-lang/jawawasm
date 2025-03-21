import net.ltgt.gradle.errorprone.errorprone

plugins {
    application
    java
    id("net.ltgt.errorprone") version "4.1.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":format"))
    implementation(project(":engine"))
    implementation(project(":runtime"))

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

        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
        error("NullAway")
    }
}

application {
    mainClass.set("dev.argon.jawawasm.app.App")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}
