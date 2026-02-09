val fabricLoaderVersion = findProperty("fabric_loader_version")?.toString() ?: "0.16.7"
val cobblemonVersion = findProperty("cobblemon_version")?.toString() ?: "1.7.1+1.21.1"
architectury {
    common("fabric", "neoforge")
}

dependencies {
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")

    // Cobblemon common module (compileOnly — not bundled)
    modCompileOnly("com.cobblemon:mod:$cobblemonVersion")

    // Architectury injectables for @ExpectPlatform
    compileOnly("dev.architectury:architectury-injectables:1.0.13")
}

val generateBuildInfo by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/source/buildinfo")
    val ver = project.version.toString()
    inputs.property("version", ver)
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile.resolve("com/catchrate")
        dir.mkdirs()
        dir.resolve("BuildInfo.kt").writeText("package com.catchrate\n\ninternal const val BUILD_VERSION = \"$ver\"\n")
    }
}

sourceSets.main {
    java.srcDir(generateBuildInfo)
}

tasks.processResources {
    inputs.property("version", project.version)
}
