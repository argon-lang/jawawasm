plugins {
    application
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":format"))
    implementation(project(":engine"))
    implementation(project(":runtime"))

    compileOnly("org.jspecify:jspecify:0.3.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(22)
    }

    withSourcesJar()
    withJavadocJar()
}

application {
    mainClass.set("dev.argon.jawawasm.app.App")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}
