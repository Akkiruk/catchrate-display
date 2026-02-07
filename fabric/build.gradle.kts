val fabricLoaderVersion = findProperty("fabric_loader_version")?.toString() ?: "0.16.7"
val fabricApiVersion = findProperty("fabric_api_version")?.toString() ?: "0.116.7+1.21.1"
val fabricKotlinVersion = findProperty("fabric_kotlin_version")?.toString() ?: "1.13.4+kotlin.2.2.0"
val cobblemonVersion = findProperty("cobblemon_version")?.toString() ?: "1.7.1+1.21.1"

plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

architectury {
    platformSetupLoomIde()
    fabric()
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
    named("developmentFabric").get().extendsFrom(common)
}

dependencies {
    common(project(path = ":common", configuration = "namedElements")) { isTransitive = false }
    shadowBundle(project(path = ":common", configuration = "transformProductionFabric"))

    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    modImplementation("net.fabricmc:fabric-language-kotlin:$fabricKotlinVersion")

    // Cobblemon Fabric artifact (compileOnly — not bundled)
    modCompileOnly("com.cobblemon:fabric:$cobblemonVersion")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
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
