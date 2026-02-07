val neoforgeVersion = findProperty("neoforge_version")?.toString() ?: "21.1.77"
val kotlinForForgeVersion = findProperty("kotlin_for_forge_version")?.toString() ?: "5.11.0"
val cobblemonVersion = findProperty("cobblemon_version")?.toString() ?: "1.7.1+1.21.1"

plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    maven("https://thedarkcolour.github.io/KotlinForForge/")
}

architectury {
    platformSetupLoomIde()
    neoForge()
}

val common: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}
val shadowBundle: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

configurations {
    compileClasspath.get().extendsFrom(common)
    runtimeClasspath.get().extendsFrom(common)
    named("developmentNeoForge").get().extendsFrom(common)
}

dependencies {
    common(project(path = ":common", configuration = "namedElements")) { isTransitive = false }
    shadowBundle(project(path = ":common", configuration = "transformProductionNeoForge"))

    "neoForge"("net.neoforged:neoforge:$neoforgeVersion")

    // Kotlin for Forge
    implementation("thedarkcolour:kotlinforforge-neoforge:$kotlinForForgeVersion")

    // Cobblemon NeoForge artifact (compileOnly — not bundled)
    modCompileOnly("com.cobblemon:neoforge:$cobblemonVersion")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    configurations = listOf(shadowBundle)
    archiveClassifier.set("dev-shadow")
}

tasks.remapJar {
    inputFile.set(tasks.shadowJar.get().archiveFile)
    archiveClassifier.set("")
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    archiveClassifier.set("dev")
    from("LICENSE")
}
