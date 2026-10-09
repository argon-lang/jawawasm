import net.ltgt.gradle.errorprone.errorprone

plugins {
    application
    java
}

dependencies {
    implementation(project(":format"))
    implementation(project(":engine"))
    implementation(project(":runtime"))

    compileOnly(libs.jspecify)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disable("DefaultCharset")
    }
}

application {
    mainClass.set("dev.argon.jawawasm.app.App")
}

tasks.named<Test>("test") {
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}
