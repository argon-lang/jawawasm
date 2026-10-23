// The test source set has no JawaWasm dependency. Only the isolated driver does.
val driver = sourceSets.create("driver")
val watCompiler = sourceSets.create("watCompiler")

dependencies {
    add(watCompiler.implementationConfigurationName, project(":format"))
    add(watCompiler.compileOnlyConfigurationName, libs.jspecify)
    add(driver.implementationConfigurationName, project(":engine"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

val driverWasm = layout.buildDirectory.file("generated/driver-resources/reflection.wasm")
val compileDriverWasm = tasks.register<JavaExec>("compileDriverWasm") {
    val wat = layout.projectDirectory.file("src/driver/wat/reflection.wat")
    val wasm = driverWasm.get().asFile
    inputs.file(wat)
    outputs.file(wasm)
    classpath = watCompiler.runtimeClasspath
    mainClass.set("dev.argon.jawawasm.reflection.generator.CompileWat")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    args(wat.asFile.absolutePath, wasm.absolutePath)
}

tasks.named<ProcessResources>(driver.processResourcesTaskName) {
    from(compileDriverWasm) {
        into("dev/argon/jawawasm/reflection/driver")
    }
}

tasks.named<Test>("test") {
    inputs.files(driver.runtimeClasspath).withPropertyName("isolatedDriverClasspath")
    dependsOn(tasks.named(driver.classesTaskName))
    systemProperty("reflectionTestClasspath", driver.runtimeClasspath.asPath)
}
