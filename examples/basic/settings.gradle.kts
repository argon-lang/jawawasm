pluginManagement {
    repositories {
        gradlePluginPortal()
    }
    // Use the checked-out plugin before its first Portal release is available.
    if (providers.gradleProperty("localJawaWasm").isPresent) {
        includeBuild("../..")
    }
}

if (providers.gradleProperty("localJawaWasm").isPresent) {
    includeBuild("../..") {
        dependencySubstitution {
            substitute(module("dev.argon.jawawasm:wasm-engine")).using(project(":engine"))
            substitute(module("dev.argon.jawawasm:wasm-format-core")).using(project(":format-core"))
            substitute(module("dev.argon.jawawasm:jawawasm-runtime")).using(project(":runtime"))
        }
    }
}

rootProject.name = "jawawasm-example-basic"
