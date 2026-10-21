plugins { id("dev.argon.jawawasm") }
wasm {
    packageName.set("example.wasm")
    val first = module("First") {
        source.set(layout.projectDirectory.file("first.wasm"))
    }
    module("Second") {
        source.set(layout.projectDirectory.file("second.wasm"))
        imports.put("first", first)
        imports.put("alias", providers.provider { first })
    }
}
