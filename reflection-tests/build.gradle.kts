// The test source set has no JawaWasm dependency. Only the isolated driver does.
val driver = sourceSets.create("driver")

dependencies {
    add(driver.implementationConfigurationName, project(":engine"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

val driverWasm = layout.buildDirectory.file("generated/driver-resources/reflection.wasm")
val compileDriverWasm = tasks.register<Exec>("compileDriverWasm") {
    val wat = layout.projectDirectory.file("src/driver/wat/reflection.wat")
    val wasmTools = rootProject.layout.projectDirectory.file("cargo-tools/bin/wasm-tools")
    val wasm = driverWasm.get().asFile
    inputs.file(wat)
    inputs.file(wasmTools)
    outputs.file(wasm)
    commandLine(wasmTools.asFile, "parse", wat.asFile, "-o", wasm)
    doFirst {
        wasm.parentFile.mkdirs()
    }
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
